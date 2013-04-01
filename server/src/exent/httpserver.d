module exent.httpserver;

public import std.regex;
public import std.socket;

import std.base64;
import std.concurrency;
import std.conv;
import std.datetime;
import std.digest.sha;
import std.file;
import std.stdio;
import std.string;

struct ServerSettings {
	ushort port = 8080;
	int maxConnections = 2;
	long maxRequestSize = 52428800;
	int connectionTimeoutMs = 180000;
	int bufferSize = 4096;
	int maxNewConnectionsFromHostPerSec = 3; // TODO: actually use this value
	int connectionQueueSize = 10;
	int maxHttpHeaderSize = 4096;
	// TODO: gzip compression, see std.zlib
}

abstract class DynamicHttpHandler : HttpHandler {
	this(Regex!char regexp) {
		super(regexp);
	}

	HttpResponse handle(HttpRequest request, Address remote);
}

abstract class StaticHttpHandler : HttpHandler {
	this(Regex!char regexp, HttpResponse response) {
		super(regexp);
		_response = response.toBytes();
	}

private:
	ubyte[] _response;
}

/* TODO: this really isn't a part of the library, should be removed */
class DynamicFileHttpHandler : DynamicHttpHandler {
	this(string path) {
		super(regex(path));
		_path = path;
	}

	override HttpResponse handle(HttpRequest request, Address remote) {
		HttpResponse response;
		response.content = cast(ubyte[]) readText(_path);
		return response;
	}

private:
	string _path;
}

struct HttpRequest {
	@property string method() {
		return _method;
	}

	@property string path() {
		return _path;
	}

	@property string query() {
		return _query;
	}

	@property string[string] headers() {
		return _headers;
	}

	@property ubyte[] content() {
		return _content;
	}

private:
	string _method;
	string _path;
	string _query;
	string[string] _headers;
	ubyte[] _content;
}

struct HttpResponse {
	int status = 200;
	ubyte[] content;

	@property string[string] headers() {
		return _headers;
	}

	bool setHeader(string key, string value) {
		if (value.length == 0) {
			_headers.remove(key);
			return true;
		}
		key = toLower(key);
		switch (key) {
		case "content-length":
			/* content-length will be set automatically */
			return false;

		default:
			_headers[key] = value;
			break;
		}
		return true;
	}

private:
	string[string] _headers;

	ubyte[] toBytes() {
		ubyte[] response = cast(ubyte[]) ("HTTP/1.1 " ~ to!string(status) ~ " " ~ to!string(status) ~ "\r\n");
		if (content.length > 0)
			_headers["content-length"] = to!string(content.length);
		foreach (key, value; _headers)
			response ~= cast(ubyte[]) (key ~ ": " ~ value ~ "\r\n");
		response ~= cast(ubyte[]) "\r\n";
		response ~= content;
		return response;
	}
}

void addDynamicHandler(DynamicHttpHandler httpHandler) {
	writefln("adding handler: %s", httpHandler);
	dynamicHttpHandlers ~= cast(shared) httpHandler;
}

void startServer(ServerSettings settings) {
	listenerThread = spawn(&listen, settings, thisTid);
}

void stopServer() {
	send(listenerThread, thisTid);
	receiveOnly!Tid();
}

/* stuff below this line is not that interesting for users */
private:
abstract class HttpHandler {
	@property auto regexp() {
		return _regexp;
	}

	@property auto matcher() {
		return _matcher;
	}

	this(Regex!char regexp) {
		_regexp = regexp;
	}

private:
	Regex!char _regexp;
	typeof(match(string.init, Regex!char.init)) _matcher;
}

class Connection {
	abstract class Protocol {
		Connection connection;

		this(Connection connection) {
			this.connection = connection;
		}

		abstract bool parseData(ubyte[] data, Address remoteAddress);
	}

	class HttpProtocol : Protocol {
		immutable webSocketMagic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
		static httpRequestStartLineRegexp = ctRegex!r"^([^ ]+) ([^ \?]+)\??([^ ]*) HTTP.*$";
		ubyte[] buffer;
		bool headersParsed;
		long contentStart;
		long contentLength;
		HttpRequest request;

		this(Connection connection) {
			super(connection);
		}

		override bool parseData(ubyte[] data, Address remoteAddress) {
			buffer ~= data;
			if (!headersParsed) {
				long pos = indexOf(cast(string) buffer, cast(string) [13, 10]);
				if (pos == -1) {
					writeln("<CR><LF> not found in first packet, presumably broken HTTP request");
					return false;
				}
				auto matcher = match(cast(string) buffer[0 .. pos], httpRequestStartLineRegexp);
				if (!matcher) {
					writeln("Start line does not match regexp, broken HTTP request");
					return false;
				}
				string startLine = cast(string) buffer[0 .. pos];
				request._method = to!string(matcher.captures[1]);
				request._path = to!string(matcher.captures[2])[1 .. $]; // chop first /
				request._query = to!string(matcher.captures[3]);
				long headerStart = pos + 2;
				pos = indexOf(cast(string) buffer[headerStart .. $], cast(string) [13, 10, 13, 10]);
				if (pos == -1) {
					if (buffer.length >= serverSettings.maxHttpHeaderSize) {
						writeln("<CR><LF><CR><LF> not found within reasonably many bytes, presumably broken HTTP request");
						return false;
					}
					writeln("<CR><LF><CR><LF> not found, presumably incomplete/broken HTTP request");
					return true;
				}
				pos += headerStart; // we only searched a slice of buffer, need to add what we skipped
				string headerText = cast(string) buffer[headerStart .. pos];
				bool hasHost;
				bool hasConnection;
				bool hasOrigin;
				bool hasUpgrade;
				string webSocketKey;
				bool hasWebSocketVersion;
				foreach (string header; splitLines(headerText)) {
					long colon = indexOf(header, ':');
					if (colon > 0 && colon < header.length - 1) {
						string key = toLower(strip(header[0 .. colon]));
						string value = strip(header[colon + 1 .. $]);
						request._headers[key] = value;
						switch (key) {
						case "host":
							hasHost = true;
							break;

						case "connection":
							hasConnection = indexOf(toLower(value), "upgrade") >= 0;
							break;

						case "origin":
							hasOrigin = true;
							break;

						case "upgrade":
							hasUpgrade = indexOf(toLower(value), "websocket") >= 0;
							break;

						case "sec-websocket-key":
							webSocketKey = value;
							break;

						case "sec-websocket-version":
							hasWebSocketVersion = value == "13";
							break;

						case "content-length":
							contentLength = to!long(value);
							if (contentLength > serverSettings.maxRequestSize)
								return false;
							break;

						default:
							/* nothing */
							break;
						}
					}
				}
				if (!hasHost) {
					writeln("No 'Host' specified in HTTP headers, not valid HTTP/1.1 request");
					return false;
				}
				headersParsed = true;
				contentStart = pos + 4;

				/* websocket? */
				if (hasConnection && hasOrigin && hasUpgrade && webSocketKey.length > 0 && hasWebSocketVersion) {
					writeln("Upgrading connection to WebSocket");
					auto shaHash = sha1Of(webSocketKey ~ webSocketMagic);
					string webSocketAccept = to!string(Base64.encode(shaHash));
					HttpResponse response;
					response.status = 101;
					response.setHeader("Upgrade", "websocket");
					response.setHeader("Connection", "Upgrade");
					response.setHeader("Sec-WebSocket-Accept", webSocketAccept);
					connection.send(response.toBytes());
					connection.changeProtocol(new WebSocketProtocol(connection));
					return true; // no longer a HTTP protocol
				}
			}
			if (contentLength >= buffer.length - contentStart) {
				request._content = buffer[contentStart .. contentStart + contentLength];
				buffer = buffer[contentStart + contentLength .. $];
				writeln("looking for handler matching path: ", request._path);
				ubyte[] response;
				foreach (shandler; staticHttpHandlers) {
					writeln("trying static handlers");
					StaticHttpHandler handler = cast(StaticHttpHandler) shandler;
					handler._matcher = match(request._path, handler._regexp);
					if (handler._matcher) {
						writeln("found matching static handler");
						response = handler._response;
						break;
					}
				}
				if (response.length == 0) {
					foreach (shandler; dynamicHttpHandlers) {
						writeln("trying dynamic handlers");
						DynamicHttpHandler handler = cast(DynamicHttpHandler) shandler;
						handler._matcher = match(request._path, handler._regexp);
						if (handler._matcher) {
							writeln("found matching dynamic handler");
							response = handler.handle(request, remoteAddress).toBytes();
							break;
						}
					}
				}
				if (response.length == 0) {
					HttpResponse httpResponse;
					httpResponse.status = 404;
					httpResponse.content = cast(ubyte[]) "Four, oh four! Nothing found :(";
					response = httpResponse.toBytes();
				}
				connection.send(response);
				request = HttpRequest();
			}
			return true;
		}
	}

	class WebSocketProtocol : Protocol {
		enum PacketType {
			CONTINUATION = 0,
			TEXT = 1,
			BINARY = 2,
			CLOSE = 8,
			PING = 9,
			PONG = 10
		}

		PacketType packetType;
		ubyte[] buffer;
		bool newFrame;
		long length;
		ubyte[] mask;

		this(Connection connection) {
			super(connection);
			newFrame = true;
		}

		override bool parseData(ubyte[] data, Address remoteAddress) {
			buffer ~= data;
			while (buffer.length > 0) {
				/* TODO: what if we get slightly more than one frame, but not enough to parse the entire header? */
				if (newFrame) {
					/* new frame */
					int pos = 0;
					//bool finalFrame = (buffer[pos] & 0b10000000) != 0; // first bit denotes whether it's the final frame or more follows, ignored for now
					if ((buffer[pos] & 0b01110000) != 0) {
						return false; // next 3 bits don't match expected binary values [000]
					}
					packetType = cast(PacketType) (buffer[pos] & 0b00001111); // next 4 bits denotes packet type
					++pos;
					if ((buffer[pos] & 0b10000000) == 0)
						return false; // next bit denotes masking, client must always set this bit
					length = (buffer[pos] & 0b01111111); // next 7 bits denotes payload size
					++pos;
					if (length == 126) {
						/* unless the 7 bits makes up the value 126, then the following 16 bits denotes length */
						length = (buffer[pos++] << 8) + buffer[pos++];
					} else if (length == 127) {
						/* or the 7 bits makes up the value 127, then the following 64 bits denotes length */
						length = (buffer[pos++] << 24) + (buffer[pos++] << 16) + (buffer[pos++] << 8) + buffer[pos++];
					}
					if (length > serverSettings.maxRequestSize)
						return false;
					mask.length = 4;
					for (int a = 0; a < 4; ++a)
						mask[a] = cast(ubyte) buffer[pos++];
					buffer = buffer[pos .. $]; // discard header from buffer
					newFrame = false;
				}
				if (buffer.length >= length) {
					/* received all data, do something! anything! */
					// TODO
					foreach (index, ref b; buffer[0 .. length])
						b ^= mask[index % 4];
					writefln("websocket message [buffer: %s/%s]: %s", length, buffer.length, cast(string) buffer[0 .. length]);
					newFrame = true;
					buffer = buffer[length .. $];
				}
			}
			return true;
		}
	}

	Socket socket;
	Address remoteAddress;
	Protocol protocol;
	ubyte[][] output;

	this(shared Socket ssocket) {
		this.socket = cast(Socket) ssocket;
		remoteAddress = socket.remoteAddress();
		socket.blocking = true;
		socket.setOption(SocketOptionLevel.SOCKET, SocketOption.RCVTIMEO, dur!"msecs"(serverSettings.connectionTimeoutMs));
		socket.setOption(SocketOptionLevel.SOCKET, SocketOption.SNDTIMEO, dur!"msecs"(serverSettings.connectionTimeoutMs));
		protocol = new HttpProtocol(this);
	}

	void changeProtocol(Protocol protocol) {
		this.protocol = protocol;
	}

	void send(ubyte[] data) {
		output ~= data;
		write();
	}

	/* returns true if connection is to be kept alive, false if it's to be closed */
	bool read() {
		ubyte[] buffer;
		buffer.length = serverSettings.bufferSize;
		ptrdiff_t read;
		bool isAlive;
		synchronized (socket) {
			read = socket.receive(buffer);
			isAlive = socket.isAlive();
		}
		if (read > 0) {
			//writeln("Read: ", cast(string) buffer[0 .. read]);
			try {
				return protocol.parseData(buffer[0 .. read], remoteAddress);
			} catch (Throwable t) {
				writefln("Exception parsing data: %s", t);
				return false;
			}
		} else if (read == 0) {
			// connection closed
			return false;
		} else if (read == Socket.ERROR && !isAlive) {
			// connection error
			writefln("Connection error with %s", remoteAddress.toString());
			return false;
		}
		return isAlive;
	}

	/* returns true if connection is to be kept alive, false if it's to be closed */
	bool write() {
		while (output.length > 0) {
			ptrdiff_t sent;
			synchronized (socket) {
				sent = socket.send(output[0]);
			}
			writefln("Writing output[%s][%s], sent = %s", output.length, output[0].length, sent);
			if (sent == Socket.ERROR) {
				// connection error
				return false;
			}
			//writeln("Wrote: ", cast(string) output[0 .. sent]);
			// remove sent bytes from output buffer
			output[0] = output[0][sent .. $];
			if (output[0].length <= 0)
				output = output[1 .. $];
		}
		return true;
	}

	void close() {
		socket.shutdown(SocketShutdown.BOTH);
		socket.close();
	}
}

ServerSettings serverSettings;
shared StaticHttpHandler[] staticHttpHandlers;
shared DynamicHttpHandler[] dynamicHttpHandlers;
//shared WebSocketHandler webSocketHandler;
Tid listenerThread;

void listen(ServerSettings settings, Tid parentTid) {
	writeln("listen: handlers.length: ", dynamicHttpHandlers.length);
	serverSettings = settings;
	Socket listener = new TcpSocket;
	scope (exit) {
		listener.shutdown(SocketShutdown.BOTH);
		listener.close();
		send(parentTid, thisTid);
	}
	listener.blocking = true;
	listener.bind(new InternetAddress(settings.port));
	listener.listen(settings.connectionQueueSize); // TODO: may fail if port is not available. also: we're currently not closing the port properly upon exiting, find out why
	listener.setOption(SocketOptionLevel.SOCKET, SocketOption.RCVTIMEO, dur!"msecs"(1000)); // don't block for more than a second at a time
	writeln("Listening on port ", settings.port);

	SysTime[Tid] threads;
	bool active = true;
	while (active) {
		try {
			threads[spawn(&handleConnection, cast(shared) listener.accept(), thisTid)] = Clock.currTime();
		} catch (SocketAcceptException e) {
			/* happens every time listener accept() times out, nothing to worry about */
		}
		bool messages = true;
		while (messages) {
			messages = receiveTimeout(dur!"msecs"(0), (Tid tid) {
				if (tid == parentTid)
					active = false; // server shutdown requested
				else
					threads.remove(tid); // socket is no longer active, remove it
			});
		}
		if (threads.length > serverSettings.maxConnections) {
			/* TODO: remove oldest last active connection(?) */
			/* with ping-ponging websocket then "last active connection" will be completely arbitrary, though */
		}
	}
	foreach (tid, activity; threads)
		send(tid, thisTid);
	while (threads.length > 0) {
		receive((Tid tid) {
			threads.remove(tid); // socket is no longer active, remove it
		});
	}
}

void handleConnection(shared Socket ssocket, Tid parentTid) {
	Connection connection = new Connection(ssocket);
	scope (exit) {
		connection.close();
		send(parentTid, thisTid);
	}
	bool active = true;
	while (active) {
		active = connection.read();
		receiveTimeout(dur!"msecs"(0), (Tid tid) {
			if (tid == parentTid)
				active = false; // server shutdown requested
		});
	}
	writefln("Closing connection with %s", connection.remoteAddress.toString());
}
