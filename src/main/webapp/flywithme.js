var DB = {
  set: (key, value) => {
    localStorage.setItem(key, value);
  },

  get: (key) => {
    return localStorage.getItem(key);
  },

  compress: (key, value) => {
    DB.set(key, LZString.compressToUTF16(value));
  },

  decompress: (key) => {
    return LZString.decompressFromUTF16(DB.get(key));
  }
}

var FWM = {
  googleMap: null,
  infoWindow: null,
  takeoffs: {},
  dividers: {
    horizontal: "50px",
    vertical: "500px"
  },

  /* FWM.active keeps track of user's current selection */
  active: {
    sortedTakeoffs: [],
    searchText: "",
    takeoff: {},
    forecast: {}
  },

  // get takeoff data, update if necessary
  updateTakeoffData: () => {
    var lastUpdated = DB.get("takeoffs_updated") || 0;
    var now = new Date().getTime();
    FWM.takeoffs = JSON.parse(DB.decompress("takeoffs") || "{}");
    if (Math.ceil((now - lastUpdated) / 86400000) > 5) {
      fetch("/takeoffs?lastUpdated=" + lastUpdated)
        .then((response) => response.json())
        .then((data) => {
          var count = 0;
          for (i in data) {
            var takeoff = data[i];
            FWM.takeoffs[takeoff.id] = takeoff;
            ++count;
            if (takeoff.lastUpdated > lastUpdated) {
              lastUpdated = takeoff.lastUpdated
            }
          }
          console.log("Updated takeoffs:", count);
          DB.compress("takeoffs", JSON.stringify(FWM.takeoffs));
          DB.set("takeoffs_updated", lastUpdated);
          FWM.sortTakeoffs();
          var markers = Object.values(FWM.takeoffs).map((takeoff) => {
            FWM.infoWindow = new google.maps.InfoWindow({});
            var marker = new google.maps.Marker({
              position: {lat: takeoff.lat, lng: takeoff.lng},
              title: takeoff.name,
              label: takeoff.name[0],
              icon: {
                url: 'data:image/svg+xml;charset=UTF-8,' + encodeURIComponent(FWM.takeoffExitsToSvg(takeoff.exits)),
                scaledSize: new google.maps.Size(40, 40),
                anchor: new google.maps.Point(20, 20)
              }
            });
            marker.addListener('click', () => {
              FWM.infoWindow.setContent("<div><h1>" + takeoff.name + "</h1><p>" + FWM.textToHtml(takeoff.desc) + "</p></div>");
              FWM.infoWindow.open(FWM.googleMap, marker);
            });
            return marker;
          });
          var markerClusterer = new MarkerClusterer(FWM.googleMap, markers, {imagePath: "libs/google_maps_v3/"});
          // the following prevent zooming in when dragging the map where initial click was on a marker
          google.maps.event.addListener(FWM.googleMap, 'dragstart', () => {
            markerClusterer.zoomOnClick_ = false;
          });
          google.maps.event.addListener(FWM.googleMap, 'mouseup', () => {
            setTimeout(() => {
              markerClusterer.zoomOnClick_ = true;
            }, 50);
          });
        });
    }
  },

  // convert text to html
  textToHtml: (text) => {
    if (!text) {
      return text;
    }
    // escape dangerous characters
    text = text.replace(/"/g, "&#34;").replace(/'/g, "&#39;").replace(/</g, "&#60;").replace(/>/g, "&#62;");
    // add links to urls
    text = text.replace(/(http:\/\/[^\s]+)/g, "<a href=\"\$1\">\$1</a>");
    // replace newlines with <br />
    text = text.replace(/\n/g, "<br />");
    return text;
  },

  // create SVG of takeoff exits
  takeoffExitsToSvg: (exits, style) => {
    return "<svg xmlns='http://www.w3.org/2000/svg' viewBox='-105 -105 210 210' style='" + (style || "") + "'>" +
      "<circle r='104' stroke='black' stroke-width='2' fill='none'></circle>" +
      "<path visibility='" + ((exits & (1 << 7)) != 0 ? "visible" : "hidden") + "' d='M 0 0 L -38.268 -92.388 A 100 100 0 0 1  38.268 -92.388 Z' stroke='none' fill='#05c70f'></path>" +
      "<path visibility='" + ((exits & (1 << 6)) != 0 ? "visible" : "hidden") + "' d='M 0 0 L  38.268 -92.388 A 100 100 0 0 1  92.388 -38.268 Z' stroke='none' fill='#05c70f'></path>" +
      "<path visibility='" + ((exits & (1 << 5)) != 0 ? "visible" : "hidden") + "' d='M 0 0 L  92.388 -38.268 A 100 100 0 0 1  92.388  38.268 Z' stroke='none' fill='#05c70f'></path>" +
      "<path visibility='" + ((exits & (1 << 4)) != 0 ? "visible" : "hidden") + "' d='M 0 0 L  92.388  38.268 A 100 100 0 0 1  38.268  92.388 Z' stroke='none' fill='#05c70f'></path>" +
      "<path visibility='" + ((exits & (1 << 3)) != 0 ? "visible" : "hidden") + "' d='M 0 0 L  38.268  92.388 A 100 100 0 0 1 -38.268  92.388 Z' stroke='none' fill='#05c70f'></path>" +
      "<path visibility='" + ((exits & (1 << 2)) != 0 ? "visible" : "hidden") + "' d='M 0 0 L -38.268  92.388 A 100 100 0 0 1 -92.388  38.268 Z' stroke='none' fill='#05c70f'></path>" +
      "<path visibility='" + ((exits & (1 << 1)) != 0 ? "visible" : "hidden") + "' d='M 0 0 L -92.388  38.268 A 100 100 0 0 1 -92.388 -38.268 Z' stroke='none' fill='#05c70f'></path>" +
      "<path visibility='" + ((exits & (1 << 0)) != 0 ? "visible" : "hidden") + "' d='M 0 0 L -92.388 -38.268 A 100 100 0 0 1 -38.268 -92.388 Z' stroke='none' fill='#05c70f'></path>" +
      "</svg>";
  },

  // toggle takeoff favouritability
  toggleFavourite: (takeoff) => {
    takeoff.favourite = !takeoff.favourite;
    DB.compress("flightlog_takeoffs", JSON.stringify(FWM.takeoffs));
    FWM.sortTakeoffs();
  },

  fetchMeteogram: (takeoff) => {
    fetch("/takeoffs/" + takeoff.id + "/meteogram")
      .then((response) => response.json())
      .then((data) => {
        FWM.active.forecast.meteogram = "data:image/gif;base64," + data.image;
        m.redraw();
      });
  },

  fetchSounding: (takeoff, timestamp) => {
    fetch("/takeoffs/" + takeoff.id + "/sounding/" + timestamp)
      .then((response) => response.json())
      .then((data) => {
        FWM.active.forecast.sounding = "data:image/gif;base64," + data[0].image;
        FWM.active.forecast.theta = "data:image/gif;base64," + data[1].image;
        FWM.active.forecast.text = "data:image/gif;base64," + data[2].image;
        m.redraw();
      });
  },

  sortTakeoffs: () => {
    FWM.active.sortedTakeoffs = Object.values(FWM.takeoffs).filter((takeoff) => takeoff.name.match(new RegExp(FWM.active.searchText, "i"))).sort(FWM.takeoffSortComparator).slice(0, 20);
    m.redraw();
  },

  // sort takeoff based on favouritability, distance & name
  takeoffSortComparator: (a, b) => {
    if (a.favourite && !b.favourite) {
      return -1;
    } else if (!a.favourite && b.favourite) {
      return 1;
    } else {
      return ("" + a.name).localeCompare(b.name);
    }
  }
};

var takeoffListEntry = {
  view: (vnode) => {
    var takeoff = vnode.attrs.takeoff;
    return [
      m.trust(FWM.takeoffExitsToSvg(takeoff.exits, "position: relative; width: 40px; height: 40px")),
      m("span", {style: {
        position: "absolute",
        left: "50px",
        right: "150px",
        height: "20px",
        "white-space": "nowrap",
        overflow: "hidden"
      }}, takeoff.name),
      m("span", {style: {
        position: "absolute",
        top: "20px",
        left: "50px",
        width: "110px",
        height: "20px",
        "white-space": "nowrap",
        overflow: "hidden"
      }}, "Height: " + takeoff.asl),
      m("span", {style: {
        position: "absolute",
        top: "20px",
        left: "180px",
        width: "100px",
        height: "20px",
        "white-space": "nowrap",
        overflow: "hidden"
      }}, "Diff: " + takeoff.height),
      m("svg", {xmlns: "http://www.w3.org/2000/svg", style: {
        position: "absolute",
        right: "48px",
        width: "40px",
        height: "40px",
        cursor: "pointer"
      }, viewBox: "0 0 51 48", onclick: (e) => {FWM.toggleFavourite(takeoff); e.stopPropagation();}}, [
        m("path", {fill: takeoff.favourite ? "yellow" : "none", stroke: "#000", d: "m25,1 6,17h18l-14,11 5,17-15-10-15,10 5-17-14-11h18z"})
      ]),
      m("img", {style: {
        position: "absolute",
        right: "5px",
        width: "40px",
        height: "40px",
        cursor: "pointer"
      }, src: "images/NOAA.svg", onclick: (e) => {FWM.fetchMeteogram(takeoff); FWM.fetchSounding(takeoff, new Date().getTime() + 10800000); e.stopPropagation();}}),
      m("div", {style: {
        position: "relative",
        display: FWM.active.takeoff.id == takeoff.id ? "block" : "none"
      }}, m.trust(FWM.textToHtml(takeoff.desc)))
    ];
  }
};

var takeoffListView = {
  view: (vnode) => {
    return FWM.active.sortedTakeoffs.map((takeoff, index) => {
      return m("div", {id: takeoff.id, key: takeoff.id, style: {
        position: "relative",
        cursor: "pointer",
        "background-color": index % 2 == 0 ? "#fff" : "#ddd"
      }, onclick: () => {
        if (FWM.active.takeoff.id == takeoff.id) {
          FWM.googleMap.panTo(FWM.active.prevMapCenter);
          FWM.googleMap.setZoom(FWM.active.prevMapZoom);
          FWM.active.takeoff = {};
        } else {
          if (FWM.active.takeoff.id == null) {
            FWM.active.prevMapCenter = FWM.googleMap.getCenter();
            FWM.active.prevMapZoom = FWM.googleMap.getZoom();
          }
          FWM.active.takeoff = takeoff;
          FWM.googleMap.panTo(takeoff);
          FWM.googleMap.setZoom(14);
        }
      }}, m(takeoffListEntry, {takeoff: takeoff}));
    });
  }
};

var googleMapView = {
  oncreate: (vnode) => {
    FWM.googleMap = new google.maps.Map(vnode.dom, {zoom: 11, center: {lat: 61.87416667, lng: 9.15472222}, mapTypeId: 'terrain'});
    //FWM.googleMap.data.loadGeoJson('https://raw.githubusercontent.com/relet/pg-xc/master/geojson/luftrom.geojson');
  },

  view: (vnode) => {
    return m("div", {id: "google-map-view", style: {height: "100%"}});
  }
};

var forecastView = {
  view: (vnode) => {
    return [
      m("button", {
        onclick: () => FWM.active.forecast = {}
      }, "Close"),
      m("img", {
        name: "meteogram",
        src: FWM.active.forecast.meteogram
      }),
      m("img", {
        name: "sounding",
        src: FWM.active.forecast.sounding
      }),
      m("img", {
        name: "theta",
        src: FWM.active.forecast.theta
      }),
      m("img", {
        name: "text",
        src: FWM.active.forecast.text
      })
    ];
  }
};

var nav = {
  view: (vnode) => {
    return [
      m("img", {
        src: "images/logo.png",
        height: "100%",
        "object-fit": "contain"
      }),
      m("img", {
        src: "images/GoogleMaps.svg",
        height: "100%",
        "object-fit": "contain"
      }),
      m("div", {
        style: {
          position: "absolute",
          top: "0",
          left: "100px",
          right: "71px"
        }
      }, m("input", {
          style: {
            height: "100%",
            width: "100%"
          },
          placeholder: "Search",
          value: FWM.active.searchText,
          onblur: (el) => {setTimeout(() => {el.target.focus()}, 10)},
          oninput: m.withAttr("value", (text) => {FWM.active.searchText = text; FWM.sortTakeoffs();})
        })
      ),
      m("svg", {xmlns: "http://www.w3.org/2000/svg", viewBox: "-105 -105 210 210", style: {
        position: "absolute",
        top: "0",
        right: "0",
        width: "50px",
        height: "50px",
        cursor: "pointer"
      }}, [
        m("line", {x1: "-60", y1: "-40", x2: "60", y2: "-40", stroke: "black", "stroke-width": "20"}),
        m("line", {x1: "-60", y1: "40", x2: "60", y2: "40", stroke: "black", "stroke-width": "20"})
      ])
    ];
  }
};

var main = {
  oninit: (vnode) => {
    FWM.updateTakeoffData();
    FWM.sortTakeoffs();
  },

  view: (vnode) => {
    return [
      m("div", {style: {
        position: "absolute",
        top: FWM.dividers.horizontal,
        left: "0",
        bottom: "0",
        width: FWM.dividers.vertical,
        overflow: "auto",
        "overflow-x": "hidden"
      }}, m(takeoffListView)),
      m("div", {style: {
        position: "absolute",
        top: "0",
        left: FWM.dividers.vertical,
        right: "0",
        bottom: "0"
      }}, m(googleMapView)),
      m("div", {style: {
        display: (FWM.active.forecast.meteogram ? "block" : "none"),
        position: "absolute",
        top: "0",
        left: FWM.dividers.vertical,
        bottom: "0",
        right: "0",
        overflow: "auto",
        background: "white"
      }}, m(forecastView))
    ];
  },
};

var body = {
  view: (vnode) => {
    return [
      m("nav", {style: {
        position: "absolute",
        top: "0",
        left: "0",
        height: FWM.dividers.horizontal,
        width: FWM.dividers.vertical,
        background: "lightskyblue"
      }}, m(nav)),
      m("main", m(main))
    ];
  }
}

// mount main
m.mount(document.body, body);
