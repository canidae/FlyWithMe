/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
/*
 * This code was generated by https://code.google.com/p/google-apis-client-generator/
 * (build: 2015-03-26 20:30:19 UTC)
 * on 2015-05-05 at 17:12:21 UTC 
 * Modify at your own risk.
 */

package net.exent.flywithme.server.flywithme;

/**
 * Service definition for Flywithme (v1).
 *
 * <p>
 * This is an API
 * </p>
 *
 * <p>
 * For more information about this service, see the
 * <a href="" target="_blank">API Documentation</a>
 * </p>
 *
 * <p>
 * This service uses {@link FlywithmeRequestInitializer} to initialize global parameters via its
 * {@link Builder}.
 * </p>
 *
 * @since 1.3
 * @author Google, Inc.
 */
@SuppressWarnings("javadoc")
public class Flywithme extends com.google.api.client.googleapis.services.json.AbstractGoogleJsonClient {

  // Note: Leave this static initializer at the top of the file.
  static {
    com.google.api.client.util.Preconditions.checkState(
        com.google.api.client.googleapis.GoogleUtils.MAJOR_VERSION == 1 &&
        com.google.api.client.googleapis.GoogleUtils.MINOR_VERSION >= 15,
        "You are currently running with version %s of google-api-client. " +
        "You need at least version 1.15 of google-api-client to run version " +
        "1.20.0 of the flywithme library.", com.google.api.client.googleapis.GoogleUtils.VERSION);
  }

  /**
   * The default encoded root URL of the service. This is determined when the library is generated
   * and normally should not be changed.
   *
   * @since 1.7
   */
  public static final String DEFAULT_ROOT_URL = "https://flywithme-server.appspot.com/_ah/api/";

  /**
   * The default encoded service path of the service. This is determined when the library is
   * generated and normally should not be changed.
   *
   * @since 1.7
   */
  public static final String DEFAULT_SERVICE_PATH = "flywithme/v1/";

  /**
   * The default encoded base URL of the service. This is determined when the library is generated
   * and normally should not be changed.
   */
  public static final String DEFAULT_BASE_URL = DEFAULT_ROOT_URL + DEFAULT_SERVICE_PATH;

  /**
   * Constructor.
   *
   * <p>
   * Use {@link Builder} if you need to specify any of the optional parameters.
   * </p>
   *
   * @param transport HTTP transport, which should normally be:
   *        <ul>
   *        <li>Google App Engine:
   *        {@code com.google.api.client.extensions.appengine.http.UrlFetchTransport}</li>
   *        <li>Android: {@code newCompatibleTransport} from
   *        {@code com.google.api.client.extensions.android.http.AndroidHttp}</li>
   *        <li>Java: {@link com.google.api.client.googleapis.javanet.GoogleNetHttpTransport#newTrustedTransport()}
   *        </li>
   *        </ul>
   * @param jsonFactory JSON factory, which may be:
   *        <ul>
   *        <li>Jackson: {@code com.google.api.client.json.jackson2.JacksonFactory}</li>
   *        <li>Google GSON: {@code com.google.api.client.json.gson.GsonFactory}</li>
   *        <li>Android Honeycomb or higher:
   *        {@code com.google.api.client.extensions.android.json.AndroidJsonFactory}</li>
   *        </ul>
   * @param httpRequestInitializer HTTP request initializer or {@code null} for none
   * @since 1.7
   */
  public Flywithme(com.google.api.client.http.HttpTransport transport, com.google.api.client.json.JsonFactory jsonFactory,
      com.google.api.client.http.HttpRequestInitializer httpRequestInitializer) {
    this(new Builder(transport, jsonFactory, httpRequestInitializer));
  }

  /**
   * @param builder builder
   */
  Flywithme(Builder builder) {
    super(builder);
  }

  @Override
  protected void initialize(com.google.api.client.googleapis.services.AbstractGoogleClientRequest<?> httpClientRequest) throws java.io.IOException {
    super.initialize(httpClientRequest);
  }

  /**
   * An accessor for creating requests from the FlyWithMeEndpoint collection.
   *
   * <p>The typical use is:</p>
   * <pre>
   *   {@code Flywithme flywithme = new Flywithme(...);}
   *   {@code Flywithme.FlyWithMeEndpoint.List request = flywithme.flyWithMeEndpoint().list(parameters ...)}
   * </pre>
   *
   * @return the resource collection
   */
  public FlyWithMeEndpoint flyWithMeEndpoint() {
    return new FlyWithMeEndpoint();
  }

  /**
   * The "flyWithMeEndpoint" collection of methods.
   */
  public class FlyWithMeEndpoint {

    /**
     * Create a request for the method "flyWithMeEndpoint.sendMessage".
     *
     * This request holds the parameters needed by the flywithme server.  After setting any optional
     * parameters, call the {@link SendMessage#execute()} method to invoke the remote operation.
     *
     * @param message
     * @return the request
     */
    public SendMessage sendMessage(java.lang.String message) throws java.io.IOException {
      SendMessage result = new SendMessage(message);
      initialize(result);
      return result;
    }

    public class SendMessage extends FlywithmeRequest<Void> {

      private static final String REST_PATH = "sendMessage/{message}";

      /**
       * Create a request for the method "flyWithMeEndpoint.sendMessage".
       *
       * This request holds the parameters needed by the the flywithme server.  After setting any
       * optional parameters, call the {@link SendMessage#execute()} method to invoke the remote
       * operation. <p> {@link
       * SendMessage#initialize(com.google.api.client.googleapis.services.AbstractGoogleClientRequest)}
       * must be called to initialize this instance immediately after invoking the constructor. </p>
       *
       * @param message
       * @since 1.13
       */
      protected SendMessage(java.lang.String message) {
        super(Flywithme.this, "POST", REST_PATH, null, Void.class);
        this.message = com.google.api.client.util.Preconditions.checkNotNull(message, "Required parameter message must be specified.");
      }

      @Override
      public SendMessage setAlt(java.lang.String alt) {
        return (SendMessage) super.setAlt(alt);
      }

      @Override
      public SendMessage setFields(java.lang.String fields) {
        return (SendMessage) super.setFields(fields);
      }

      @Override
      public SendMessage setKey(java.lang.String key) {
        return (SendMessage) super.setKey(key);
      }

      @Override
      public SendMessage setOauthToken(java.lang.String oauthToken) {
        return (SendMessage) super.setOauthToken(oauthToken);
      }

      @Override
      public SendMessage setPrettyPrint(java.lang.Boolean prettyPrint) {
        return (SendMessage) super.setPrettyPrint(prettyPrint);
      }

      @Override
      public SendMessage setQuotaUser(java.lang.String quotaUser) {
        return (SendMessage) super.setQuotaUser(quotaUser);
      }

      @Override
      public SendMessage setUserIp(java.lang.String userIp) {
        return (SendMessage) super.setUserIp(userIp);
      }

      @com.google.api.client.util.Key
      private java.lang.String message;

      /**

       */
      public java.lang.String getMessage() {
        return message;
      }

      public SendMessage setMessage(java.lang.String message) {
        this.message = message;
        return this;
      }

      @Override
      public SendMessage set(String parameterName, Object value) {
        return (SendMessage) super.set(parameterName, value);
      }
    }

  }

  /**
   * Create a request for the method "listDevices".
   *
   * This request holds the parameters needed by the flywithme server.  After setting any optional
   * parameters, call the {@link ListDevices#execute()} method to invoke the remote operation.
   *
   * @param count
   * @return the request
   */
  public ListDevices listDevices(java.lang.Integer count) throws java.io.IOException {
    ListDevices result = new ListDevices(count);
    initialize(result);
    return result;
  }

  public class ListDevices extends FlywithmeRequest<net.exent.flywithme.server.flywithme.model.CollectionResponsePilot> {

    private static final String REST_PATH = "pilot/{count}";

    /**
     * Create a request for the method "listDevices".
     *
     * This request holds the parameters needed by the the flywithme server.  After setting any
     * optional parameters, call the {@link ListDevices#execute()} method to invoke the remote
     * operation. <p> {@link
     * ListDevices#initialize(com.google.api.client.googleapis.services.AbstractGoogleClientRequest)}
     * must be called to initialize this instance immediately after invoking the constructor. </p>
     *
     * @param count
     * @since 1.13
     */
    protected ListDevices(java.lang.Integer count) {
      super(Flywithme.this, "GET", REST_PATH, null, net.exent.flywithme.server.flywithme.model.CollectionResponsePilot.class);
      this.count = com.google.api.client.util.Preconditions.checkNotNull(count, "Required parameter count must be specified.");
    }

    @Override
    public com.google.api.client.http.HttpResponse executeUsingHead() throws java.io.IOException {
      return super.executeUsingHead();
    }

    @Override
    public com.google.api.client.http.HttpRequest buildHttpRequestUsingHead() throws java.io.IOException {
      return super.buildHttpRequestUsingHead();
    }

    @Override
    public ListDevices setAlt(java.lang.String alt) {
      return (ListDevices) super.setAlt(alt);
    }

    @Override
    public ListDevices setFields(java.lang.String fields) {
      return (ListDevices) super.setFields(fields);
    }

    @Override
    public ListDevices setKey(java.lang.String key) {
      return (ListDevices) super.setKey(key);
    }

    @Override
    public ListDevices setOauthToken(java.lang.String oauthToken) {
      return (ListDevices) super.setOauthToken(oauthToken);
    }

    @Override
    public ListDevices setPrettyPrint(java.lang.Boolean prettyPrint) {
      return (ListDevices) super.setPrettyPrint(prettyPrint);
    }

    @Override
    public ListDevices setQuotaUser(java.lang.String quotaUser) {
      return (ListDevices) super.setQuotaUser(quotaUser);
    }

    @Override
    public ListDevices setUserIp(java.lang.String userIp) {
      return (ListDevices) super.setUserIp(userIp);
    }

    @com.google.api.client.util.Key
    private java.lang.Integer count;

    /**

     */
    public java.lang.Integer getCount() {
      return count;
    }

    public ListDevices setCount(java.lang.Integer count) {
      this.count = count;
      return this;
    }

    @Override
    public ListDevices set(String parameterName, Object value) {
      return (ListDevices) super.set(parameterName, value);
    }
  }

  /**
   * Create a request for the method "registerPilot".
   *
   * This request holds the parameters needed by the flywithme server.  After setting any optional
   * parameters, call the {@link RegisterPilot#execute()} method to invoke the remote operation.
   *
   * @param pilotId
   * @return the request
   */
  public RegisterPilot registerPilot(java.lang.String pilotId) throws java.io.IOException {
    RegisterPilot result = new RegisterPilot(pilotId);
    initialize(result);
    return result;
  }

  public class RegisterPilot extends FlywithmeRequest<Void> {

    private static final String REST_PATH = "registerPilot/{pilotId}";

    /**
     * Create a request for the method "registerPilot".
     *
     * This request holds the parameters needed by the the flywithme server.  After setting any
     * optional parameters, call the {@link RegisterPilot#execute()} method to invoke the remote
     * operation. <p> {@link RegisterPilot#initialize(com.google.api.client.googleapis.services.Abstra
     * ctGoogleClientRequest)} must be called to initialize this instance immediately after invoking
     * the constructor. </p>
     *
     * @param pilotId
     * @since 1.13
     */
    protected RegisterPilot(java.lang.String pilotId) {
      super(Flywithme.this, "POST", REST_PATH, null, Void.class);
      this.pilotId = com.google.api.client.util.Preconditions.checkNotNull(pilotId, "Required parameter pilotId must be specified.");
    }

    @Override
    public RegisterPilot setAlt(java.lang.String alt) {
      return (RegisterPilot) super.setAlt(alt);
    }

    @Override
    public RegisterPilot setFields(java.lang.String fields) {
      return (RegisterPilot) super.setFields(fields);
    }

    @Override
    public RegisterPilot setKey(java.lang.String key) {
      return (RegisterPilot) super.setKey(key);
    }

    @Override
    public RegisterPilot setOauthToken(java.lang.String oauthToken) {
      return (RegisterPilot) super.setOauthToken(oauthToken);
    }

    @Override
    public RegisterPilot setPrettyPrint(java.lang.Boolean prettyPrint) {
      return (RegisterPilot) super.setPrettyPrint(prettyPrint);
    }

    @Override
    public RegisterPilot setQuotaUser(java.lang.String quotaUser) {
      return (RegisterPilot) super.setQuotaUser(quotaUser);
    }

    @Override
    public RegisterPilot setUserIp(java.lang.String userIp) {
      return (RegisterPilot) super.setUserIp(userIp);
    }

    @com.google.api.client.util.Key
    private java.lang.String pilotId;

    /**

     */
    public java.lang.String getPilotId() {
      return pilotId;
    }

    public RegisterPilot setPilotId(java.lang.String pilotId) {
      this.pilotId = pilotId;
      return this;
    }

    @Override
    public RegisterPilot set(String parameterName, Object value) {
      return (RegisterPilot) super.set(parameterName, value);
    }
  }

  /**
   * Create a request for the method "scheduleFlight".
   *
   * This request holds the parameters needed by the flywithme server.  After setting any optional
   * parameters, call the {@link ScheduleFlight#execute()} method to invoke the remote operation.
   *
   * @param pilotId
   * @param takeoffId
   * @param timestamp
   * @return the request
   */
  public ScheduleFlight scheduleFlight(java.lang.String pilotId, java.lang.Integer takeoffId, java.lang.Integer timestamp) throws java.io.IOException {
    ScheduleFlight result = new ScheduleFlight(pilotId, takeoffId, timestamp);
    initialize(result);
    return result;
  }

  public class ScheduleFlight extends FlywithmeRequest<Void> {

    private static final String REST_PATH = "scheduleFlight/{pilotId}/{takeoffId}/{timestamp}";

    /**
     * Create a request for the method "scheduleFlight".
     *
     * This request holds the parameters needed by the the flywithme server.  After setting any
     * optional parameters, call the {@link ScheduleFlight#execute()} method to invoke the remote
     * operation. <p> {@link ScheduleFlight#initialize(com.google.api.client.googleapis.services.Abstr
     * actGoogleClientRequest)} must be called to initialize this instance immediately after invoking
     * the constructor. </p>
     *
     * @param pilotId
     * @param takeoffId
     * @param timestamp
     * @since 1.13
     */
    protected ScheduleFlight(java.lang.String pilotId, java.lang.Integer takeoffId, java.lang.Integer timestamp) {
      super(Flywithme.this, "POST", REST_PATH, null, Void.class);
      this.pilotId = com.google.api.client.util.Preconditions.checkNotNull(pilotId, "Required parameter pilotId must be specified.");
      this.takeoffId = com.google.api.client.util.Preconditions.checkNotNull(takeoffId, "Required parameter takeoffId must be specified.");
      this.timestamp = com.google.api.client.util.Preconditions.checkNotNull(timestamp, "Required parameter timestamp must be specified.");
    }

    @Override
    public ScheduleFlight setAlt(java.lang.String alt) {
      return (ScheduleFlight) super.setAlt(alt);
    }

    @Override
    public ScheduleFlight setFields(java.lang.String fields) {
      return (ScheduleFlight) super.setFields(fields);
    }

    @Override
    public ScheduleFlight setKey(java.lang.String key) {
      return (ScheduleFlight) super.setKey(key);
    }

    @Override
    public ScheduleFlight setOauthToken(java.lang.String oauthToken) {
      return (ScheduleFlight) super.setOauthToken(oauthToken);
    }

    @Override
    public ScheduleFlight setPrettyPrint(java.lang.Boolean prettyPrint) {
      return (ScheduleFlight) super.setPrettyPrint(prettyPrint);
    }

    @Override
    public ScheduleFlight setQuotaUser(java.lang.String quotaUser) {
      return (ScheduleFlight) super.setQuotaUser(quotaUser);
    }

    @Override
    public ScheduleFlight setUserIp(java.lang.String userIp) {
      return (ScheduleFlight) super.setUserIp(userIp);
    }

    @com.google.api.client.util.Key
    private java.lang.String pilotId;

    /**

     */
    public java.lang.String getPilotId() {
      return pilotId;
    }

    public ScheduleFlight setPilotId(java.lang.String pilotId) {
      this.pilotId = pilotId;
      return this;
    }

    @com.google.api.client.util.Key
    private java.lang.Integer takeoffId;

    /**

     */
    public java.lang.Integer getTakeoffId() {
      return takeoffId;
    }

    public ScheduleFlight setTakeoffId(java.lang.Integer takeoffId) {
      this.takeoffId = takeoffId;
      return this;
    }

    @com.google.api.client.util.Key
    private java.lang.Integer timestamp;

    /**

     */
    public java.lang.Integer getTimestamp() {
      return timestamp;
    }

    public ScheduleFlight setTimestamp(java.lang.Integer timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    @Override
    public ScheduleFlight set(String parameterName, Object value) {
      return (ScheduleFlight) super.set(parameterName, value);
    }
  }

  /**
   * Create a request for the method "unregisterPilot".
   *
   * This request holds the parameters needed by the flywithme server.  After setting any optional
   * parameters, call the {@link UnregisterPilot#execute()} method to invoke the remote operation.
   *
   * @param pilotId
   * @return the request
   */
  public UnregisterPilot unregisterPilot(java.lang.String pilotId) throws java.io.IOException {
    UnregisterPilot result = new UnregisterPilot(pilotId);
    initialize(result);
    return result;
  }

  public class UnregisterPilot extends FlywithmeRequest<Void> {

    private static final String REST_PATH = "unregisterPilot/{pilotId}";

    /**
     * Create a request for the method "unregisterPilot".
     *
     * This request holds the parameters needed by the the flywithme server.  After setting any
     * optional parameters, call the {@link UnregisterPilot#execute()} method to invoke the remote
     * operation. <p> {@link UnregisterPilot#initialize(com.google.api.client.googleapis.services.Abst
     * ractGoogleClientRequest)} must be called to initialize this instance immediately after invoking
     * the constructor. </p>
     *
     * @param pilotId
     * @since 1.13
     */
    protected UnregisterPilot(java.lang.String pilotId) {
      super(Flywithme.this, "POST", REST_PATH, null, Void.class);
      this.pilotId = com.google.api.client.util.Preconditions.checkNotNull(pilotId, "Required parameter pilotId must be specified.");
    }

    @Override
    public UnregisterPilot setAlt(java.lang.String alt) {
      return (UnregisterPilot) super.setAlt(alt);
    }

    @Override
    public UnregisterPilot setFields(java.lang.String fields) {
      return (UnregisterPilot) super.setFields(fields);
    }

    @Override
    public UnregisterPilot setKey(java.lang.String key) {
      return (UnregisterPilot) super.setKey(key);
    }

    @Override
    public UnregisterPilot setOauthToken(java.lang.String oauthToken) {
      return (UnregisterPilot) super.setOauthToken(oauthToken);
    }

    @Override
    public UnregisterPilot setPrettyPrint(java.lang.Boolean prettyPrint) {
      return (UnregisterPilot) super.setPrettyPrint(prettyPrint);
    }

    @Override
    public UnregisterPilot setQuotaUser(java.lang.String quotaUser) {
      return (UnregisterPilot) super.setQuotaUser(quotaUser);
    }

    @Override
    public UnregisterPilot setUserIp(java.lang.String userIp) {
      return (UnregisterPilot) super.setUserIp(userIp);
    }

    @com.google.api.client.util.Key
    private java.lang.String pilotId;

    /**

     */
    public java.lang.String getPilotId() {
      return pilotId;
    }

    public UnregisterPilot setPilotId(java.lang.String pilotId) {
      this.pilotId = pilotId;
      return this;
    }

    @Override
    public UnregisterPilot set(String parameterName, Object value) {
      return (UnregisterPilot) super.set(parameterName, value);
    }
  }

  /**
   * Create a request for the method "unscheduleFlight".
   *
   * This request holds the parameters needed by the flywithme server.  After setting any optional
   * parameters, call the {@link UnscheduleFlight#execute()} method to invoke the remote operation.
   *
   * @param pilotId
   * @param takeoffId
   * @param timestamp
   * @return the request
   */
  public UnscheduleFlight unscheduleFlight(java.lang.String pilotId, java.lang.Integer takeoffId, java.lang.Integer timestamp) throws java.io.IOException {
    UnscheduleFlight result = new UnscheduleFlight(pilotId, takeoffId, timestamp);
    initialize(result);
    return result;
  }

  public class UnscheduleFlight extends FlywithmeRequest<Void> {

    private static final String REST_PATH = "unscheduleFlight/{pilotId}/{takeoffId}/{timestamp}";

    /**
     * Create a request for the method "unscheduleFlight".
     *
     * This request holds the parameters needed by the the flywithme server.  After setting any
     * optional parameters, call the {@link UnscheduleFlight#execute()} method to invoke the remote
     * operation. <p> {@link UnscheduleFlight#initialize(com.google.api.client.googleapis.services.Abs
     * tractGoogleClientRequest)} must be called to initialize this instance immediately after
     * invoking the constructor. </p>
     *
     * @param pilotId
     * @param takeoffId
     * @param timestamp
     * @since 1.13
     */
    protected UnscheduleFlight(java.lang.String pilotId, java.lang.Integer takeoffId, java.lang.Integer timestamp) {
      super(Flywithme.this, "POST", REST_PATH, null, Void.class);
      this.pilotId = com.google.api.client.util.Preconditions.checkNotNull(pilotId, "Required parameter pilotId must be specified.");
      this.takeoffId = com.google.api.client.util.Preconditions.checkNotNull(takeoffId, "Required parameter takeoffId must be specified.");
      this.timestamp = com.google.api.client.util.Preconditions.checkNotNull(timestamp, "Required parameter timestamp must be specified.");
    }

    @Override
    public UnscheduleFlight setAlt(java.lang.String alt) {
      return (UnscheduleFlight) super.setAlt(alt);
    }

    @Override
    public UnscheduleFlight setFields(java.lang.String fields) {
      return (UnscheduleFlight) super.setFields(fields);
    }

    @Override
    public UnscheduleFlight setKey(java.lang.String key) {
      return (UnscheduleFlight) super.setKey(key);
    }

    @Override
    public UnscheduleFlight setOauthToken(java.lang.String oauthToken) {
      return (UnscheduleFlight) super.setOauthToken(oauthToken);
    }

    @Override
    public UnscheduleFlight setPrettyPrint(java.lang.Boolean prettyPrint) {
      return (UnscheduleFlight) super.setPrettyPrint(prettyPrint);
    }

    @Override
    public UnscheduleFlight setQuotaUser(java.lang.String quotaUser) {
      return (UnscheduleFlight) super.setQuotaUser(quotaUser);
    }

    @Override
    public UnscheduleFlight setUserIp(java.lang.String userIp) {
      return (UnscheduleFlight) super.setUserIp(userIp);
    }

    @com.google.api.client.util.Key
    private java.lang.String pilotId;

    /**

     */
    public java.lang.String getPilotId() {
      return pilotId;
    }

    public UnscheduleFlight setPilotId(java.lang.String pilotId) {
      this.pilotId = pilotId;
      return this;
    }

    @com.google.api.client.util.Key
    private java.lang.Integer takeoffId;

    /**

     */
    public java.lang.Integer getTakeoffId() {
      return takeoffId;
    }

    public UnscheduleFlight setTakeoffId(java.lang.Integer takeoffId) {
      this.takeoffId = takeoffId;
      return this;
    }

    @com.google.api.client.util.Key
    private java.lang.Integer timestamp;

    /**

     */
    public java.lang.Integer getTimestamp() {
      return timestamp;
    }

    public UnscheduleFlight setTimestamp(java.lang.Integer timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    @Override
    public UnscheduleFlight set(String parameterName, Object value) {
      return (UnscheduleFlight) super.set(parameterName, value);
    }
  }

  /**
   * Builder for {@link Flywithme}.
   *
   * <p>
   * Implementation is not thread-safe.
   * </p>
   *
   * @since 1.3.0
   */
  public static final class Builder extends com.google.api.client.googleapis.services.json.AbstractGoogleJsonClient.Builder {

    /**
     * Returns an instance of a new builder.
     *
     * @param transport HTTP transport, which should normally be:
     *        <ul>
     *        <li>Google App Engine:
     *        {@code com.google.api.client.extensions.appengine.http.UrlFetchTransport}</li>
     *        <li>Android: {@code newCompatibleTransport} from
     *        {@code com.google.api.client.extensions.android.http.AndroidHttp}</li>
     *        <li>Java: {@link com.google.api.client.googleapis.javanet.GoogleNetHttpTransport#newTrustedTransport()}
     *        </li>
     *        </ul>
     * @param jsonFactory JSON factory, which may be:
     *        <ul>
     *        <li>Jackson: {@code com.google.api.client.json.jackson2.JacksonFactory}</li>
     *        <li>Google GSON: {@code com.google.api.client.json.gson.GsonFactory}</li>
     *        <li>Android Honeycomb or higher:
     *        {@code com.google.api.client.extensions.android.json.AndroidJsonFactory}</li>
     *        </ul>
     * @param httpRequestInitializer HTTP request initializer or {@code null} for none
     * @since 1.7
     */
    public Builder(com.google.api.client.http.HttpTransport transport, com.google.api.client.json.JsonFactory jsonFactory,
        com.google.api.client.http.HttpRequestInitializer httpRequestInitializer) {
      super(
          transport,
          jsonFactory,
          DEFAULT_ROOT_URL,
          DEFAULT_SERVICE_PATH,
          httpRequestInitializer,
          false);
    }

    /** Builds a new instance of {@link Flywithme}. */
    @Override
    public Flywithme build() {
      return new Flywithme(this);
    }

    @Override
    public Builder setRootUrl(String rootUrl) {
      return (Builder) super.setRootUrl(rootUrl);
    }

    @Override
    public Builder setServicePath(String servicePath) {
      return (Builder) super.setServicePath(servicePath);
    }

    @Override
    public Builder setHttpRequestInitializer(com.google.api.client.http.HttpRequestInitializer httpRequestInitializer) {
      return (Builder) super.setHttpRequestInitializer(httpRequestInitializer);
    }

    @Override
    public Builder setApplicationName(String applicationName) {
      return (Builder) super.setApplicationName(applicationName);
    }

    @Override
    public Builder setSuppressPatternChecks(boolean suppressPatternChecks) {
      return (Builder) super.setSuppressPatternChecks(suppressPatternChecks);
    }

    @Override
    public Builder setSuppressRequiredParameterChecks(boolean suppressRequiredParameterChecks) {
      return (Builder) super.setSuppressRequiredParameterChecks(suppressRequiredParameterChecks);
    }

    @Override
    public Builder setSuppressAllChecks(boolean suppressAllChecks) {
      return (Builder) super.setSuppressAllChecks(suppressAllChecks);
    }

    /**
     * Set the {@link FlywithmeRequestInitializer}.
     *
     * @since 1.12
     */
    public Builder setFlywithmeRequestInitializer(
        FlywithmeRequestInitializer flywithmeRequestInitializer) {
      return (Builder) super.setGoogleClientRequestInitializer(flywithmeRequestInitializer);
    }

    @Override
    public Builder setGoogleClientRequestInitializer(
        com.google.api.client.googleapis.services.GoogleClientRequestInitializer googleClientRequestInitializer) {
      return (Builder) super.setGoogleClientRequestInitializer(googleClientRequestInitializer);
    }
  }
}
