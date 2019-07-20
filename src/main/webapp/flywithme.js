/* persisted data */
var DB = {
  settings: null,
  takeoffs: null,
  favourited: null
};

Object.keys(DB).forEach((storeName) => {
  var store = new idbKeyval.Store(storeName);
  var cache = {};
  var initCallbacks = [];

  DB[storeName] = {
    all: () => {
      return cache;
    },

    get: (key, defaultValue) => {
      return cache[key] || defaultValue;
    },

    set: (key, value) => {
      cache[key] = value;
      idbKeyval.set(key, value, store).catch(err => console.log(err));
    },

    addInitCallback: (callback) => {
      initCallbacks.push(callback);
    }
  };

  idbKeyval.keys(store).then(keys => {
    var promises = [];
    keys.forEach(key => {
      promises.push(idbKeyval.get(key, store).then(val => cache[key] = val).catch(err => console.log(err)));
    });
    Promise.all(promises).then(() => {
      initCallbacks.forEach(callback => {
        callback();
      });
    });
  }).catch(err => console.log(err));
});

/* event listeners */
window.addEventListener("resize", function() {
  m.redraw();
});

/* layouts */
var Layouts = {
  large: {
    panels: [
      {
        style: {
          position: "absolute",
          top: "0",
          left: "0",
          height: "50px",
          width: "500px"
        },
        panes: ["nav"]
      },
      {
        style: {
          position: "absolute",
          top: "50px",
          left: "0",
          bottom: "0",
          width: "500px"
        },
        panes: ["takeoffList", "options"]
      },
      {
        style: {
          position: "absolute",
          top: "0",
          left: "500px",
          right: "0",
          bottom: "0"
        },
        panes: ["googleMap", "forecast"]
      }
    ]
  },
  small: {
    panels: [
      {
        style: {
          position: "absolute",
          top: "0",
          left: "0",
          right: "0",
          height: "50px"
        },
        panes: ["nav"]
      },
      {
        style: {
          position: "absolute",
          top: "50px",
          left: "0",
          right: "0",
          bottom: "0"
        },
        panes: ["takeoffList", "options", "googleMap", "forecast"]
      }
    ]
  }
};

/* a single entry in the takeoff list */
var TakeoffListEntry = {
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
      m("img", {style: {
        position: "absolute",
        right: "95px",
        width: "40px",
        height: "40px",
        cursor: "pointer",
      }, src: "images/navigate.svg", onclick: (e) => {
        window.open("http://maps.google.com/maps?saddr=" + FWM.position.latitude + "," + FWM.position.longitude + "&daddr=" + takeoff.lat + "," + takeoff.lng + "&mode=driving");
        e.stopPropagation();
      }}),
      m("svg", {xmlns: "http://www.w3.org/2000/svg", style: {
        position: "absolute",
        right: "50px",
        width: "40px",
        height: "40px",
        cursor: "pointer"
      }, viewBox: "0 0 51 48", onclick: (e) => {
        FWM.toggleFavourite(takeoff);
        e.stopPropagation();
      }}, [
        m("path", {fill: takeoff.favourite ? "yellow" : "none", stroke: "#000", d: "m25,1 6,17h18l-14,11 5,17-15-10-15,10 5-17-14-11h18z"})
      ]),
      m("img", {style: {
        position: "absolute",
        right: "5px",
        width: "40px",
        height: "40px",
        animation: Forecast.loading == takeoff.id ? "loading 2s infinite" : null,
        cursor: "pointer"
      }, src: "images/NOAA.svg", onclick: (e) => {
        FWM.fetchMeteogram(takeoff);
        e.stopPropagation();
      }}),
      m("div", {style: {
        position: "relative",
        display: vnode.attrs.showDesc ? "block" : "none"
      }}, m.trust(FWM.textToHtml(takeoff.desc)))
    ];
  }
};

/* list of takeoffs */
var TakeoffList = {
  takeoff: {}, // selected takeoff

  view: (vnode) => {
    var comparator = (a, b) => {
      if (a.favourite && !b.favourite) {
        return -1;
      } else if (!a.favourite && b.favourite) {
        return 1;
      } else if (FWM.position && FWM.position.latitude && FWM.position.longitude) {
        return FWM.calculateDistance(FWM.position.latitude, FWM.position.longitude, a.lat, a.lng) - FWM.calculateDistance(FWM.position.latitude, FWM.position.longitude, b.lat, b.lng);
      } else {
        return ("" + a.name).localeCompare(b.name);
      }
    };
    var loading = FWM.takeoffs.length <= 0;
    var takeoffs = loading ? Object.values(DB.favourited.all()) : FWM.takeoffs;
    var loadingInfo = null;
    if (loading) {
      var favouriteTip = null;
      if (!takeoffs || takeoffs.length <= 0) {
        favouriteTip = m("p", {style: {
          "margin-top": "10px"
        }}, [
          m("strong", "Another tip: "),
          m("p", "Favourited takeoffs will load much quicker than other takeoffs!"),
        ]);
      }
      loadingInfo = m("div", {style: {
        height: "100%",
        "overflow-y": "auto"
      }}, [
        m("h1", {style: {
          width: "100%",
          animation: "loading 2s infinite",
          "text-align": "center"
        }}, "Loading..."),
        m("p", {style: {
          width: "100%",
          "text-align": "center"
        }}, "(probably)"),
        m("p", {style: {
          "margin-top": "10px"
        }}, [
          m("strong", "Tip: "),
          m("p", "On a mobile device? Look for \"Add to home screen\" for easy access to Fly With Me!"),
        ]),
        favouriteTip,
        m("p", {style: {
          "margin-top": "10px"
        }}, [
          m("strong", "Problems?"),
          m("p", [
            "Site is still under development, check ",
            m("a[href=https://github.com/canidae/FlyWithMe/issues]", "GitHub"),
            " for reported issues."
          ])
        ])
      ]);
    }
    return m("div", {style: {
      height: "100%",
      "overflow-y": "auto"
    }}, takeoffs.filter((takeoff) => takeoff.name.match(new RegExp(FWM.searchText, "i"))).sort(comparator).slice(0, 20).map((takeoff, index) => {
      return m("div", {id: takeoff.id, key: takeoff.id, style: {
        position: "relative",
        cursor: "pointer",
        "background-color": index % 2 == 0 ? "#fff" : "#ddd"
      }, onclick: () => {
        if (TakeoffList.takeoff.id == takeoff.id) {
          GoogleMap.moveBack();
          TakeoffList.takeoff = {};
        } else {
          TakeoffList.takeoff = takeoff;
          GoogleMap.moveTo(takeoff, 14);
        }
      }}, m(TakeoffListEntry, {takeoff: takeoff, showDesc: TakeoffList.takeoff.id == takeoff.id}));
    }), loadingInfo);
  }
};

/* map view of takeoffs */
var GoogleMap = {
  map: null,
  infoWindow: null,
  markerClusterer: null,
  prevView: {},

  oncreate: (vnode) => {
    // TODO: google may not be defined, need to do this when google is initialized
    GoogleMap.map = new google.maps.Map(vnode.dom, {zoom: 11, center: {lat: FWM.position.latitude, lng: FWM.position.longitude}, mapTypeId: 'terrain'});

    // TODO: show airspace
    //GoogleMap.map.data.loadGeoJson('https://raw.githubusercontent.com/relet/pg-xc/master/geojson/luftrom.geojson');
    
    // show takeoff map markers
    GoogleMap.updateMapMarkers();
  },

  view: (vnode) => {
    return m("div", {id: "google-map-view", style: {height: "100%"}});
  },

  moveTo: (latLng, zoom) => {
    if (GoogleMap.map) {
      GoogleMap.prevView.latLng = GoogleMap.map.getCenter();
      GoogleMap.prevView.zoom = GoogleMap.map.getZoom();
      GoogleMap.map.panTo(latLng);
      if (zoom) {
        GoogleMap.map.setZoom(zoom);
      }
    }
  },

  moveBack: () => {
    if (GoogleMap.prevView.latLng) {
      GoogleMap.moveTo(GoogleMap.prevView.latLng, GoogleMap.prevView.zoom);
      googleMap.prevView = {};
    }
  },

  updateMapMarkers: () => {
    if (GoogleMap.map == null) {
      // map not yet initialized
      return;
    }
    var markers = FWM.takeoffs.map((takeoff) => {
      GoogleMap.infoWindow = new google.maps.InfoWindow({});
      var takeoffExitsHtml = FWM.takeoffExitsToSvg(takeoff.exits, "width: 40px; height: 40px");
      var marker = new google.maps.Marker({
        position: {lat: takeoff.lat, lng: takeoff.lng},
        title: takeoff.name,
        icon: {
          url: 'data:image/svg+xml;charset=UTF-8,' + encodeURIComponent(takeoffExitsHtml),
          scaledSize: new google.maps.Size(40, 40),
          anchor: new google.maps.Point(20, 20)
        }
      });
      marker.addListener('click', () => {
        GoogleMap.infoWindow.setContent("<div><h1>" + takeoff.name + takeoffExitsHtml + "</h1><p>" + FWM.textToHtml(takeoff.desc) + "</p></div>");
        GoogleMap.infoWindow.open(GoogleMap.map, marker);
      });
      return marker;
    });
    if (GoogleMap.markerClusterer != null) {
      GoogleMap.markerClusterer.clearMarkers();
    }
    GoogleMap.markerClusterer = new MarkerClusterer(GoogleMap.map, markers, {imagePath: "libs/google_maps_v3/"});
    // the following prevent zooming in when dragging the map where initial click was on a marker
    google.maps.event.addListener(GoogleMap.map, 'dragstart', () => {
      GoogleMap.markerClusterer.zoomOnClick_ = false;
    });
    google.maps.event.addListener(GoogleMap.map, 'mouseup', () => {
      setTimeout(() => {
        GoogleMap.markerClusterer.zoomOnClick_ = true;
      }, 50);
    });
  },
};

/* forecast view for a takeoff */
var Forecast = {
  takeoff: null,
  loading: null,
  soundingHour: null,
  images: {},

  view: (vnode) => {
    var forecastOptions = [];
    forecastOptions.push(m("option", {
      value: "sounding",
      disabled: "disabled"
    }, "Sounding"));
    var dateOptions = {
      weekday: "short",
      hour: "2-digit",
      minute: "2-digit",
      timeZoneName: "short",
      hour12: false
    }
    for (i = 12; i <= 96; i += 3) {
      var soundingDate = new Date();
      soundingDate.setUTCHours(i);
      soundingDate.setUTCMinutes(0);
      forecastOptions.push(m("option", {
        value: i
      }, "Sounding " + soundingDate.toLocaleDateString(undefined, dateOptions)));
    }
    return [
      m("div", {style: {
        position: "absolute",
        top: "0",
        left: "0",
        right: "0",
        height: "50px"
      }}, [
        m("button", {
          onclick: () => {m.route.set("/");}
        }, "Close"),
        m("select", {
          selectedIndex: Forecast.soundingHour ? (Forecast.soundingHour - 9) / 3 : 0,
          onchange: m.withAttr("selectedIndex", (index) => {FWM.fetchSounding(index * 3 + 9);})
        }, forecastOptions)
      ]),
      m("div", {style: {
        position: "absolute",
        top: "50px",
        left: "0",
        right: "0",
        bottom: "0",
        "overflow-y": "hidden",
        "overflow-x": "scroll",
        "white-space": "nowrap"
      }}, [
        m("img", {
          style: {
            "max-height": "100%",
            "max-width": "100%"
          },
          name: "meteogram",
          src: Forecast.images.meteogram
        }),
        m("img", {
          style: {
            "max-height": "100%",
            "max-width": "100%"
          },
          name: "sounding",
          src: Forecast.images.sounding
        }),
        m("img", {
          style: {
            "max-height": "100%",
            "max-width": "100%"
          },
          name: "theta",
          src: Forecast.images.theta
        }),
        m("img", {
          style: {
            "max-height": "100%",
            "max-width": "100%"
          },
          name: "text",
          src: Forecast.images.text
        })
      ])
    ];
  }
};

/* user settings */
var Options = {
  view: (vnode) => {
    return [
      m("h1", "Takeoff filters"),
      m("input[type=checkbox]", {id: "hide_missing_coords", checked: DB.settings.get("hide_missing_coords"), onclick: m.withAttr("checked", () => {
        DB.settings.set("hide_missing_coords", !DB.settings.get("hide_missing_coords"));
        FWM.updateTakeoffList();
      })}),
      m("label", {"for": "hide_missing_coords"}, "Hide takeoffs with missing coordinates"),
      m("br"),
      m("input[type=checkbox]", {id: "hide_short_info", checked: DB.settings.get("hide_short_info"), onclick: m.withAttr("checked", () => {
        DB.settings.set("hide_short_info", !DB.settings.get("hide_short_info"));
        FWM.updateTakeoffList();
      })}),
      m("label", {"for": "hide_short_info"}, "Hide takeoffs with short name/description")
    ];
  }
};

var Nav = {
  view: (vnode) => {
    return [
      m("img", {
        src: "images/logo.png",
        height: "100%",
        "object-fit": "contain",
        style: {
          cursor: "pointer"
        },
        onclick: () => {
          m.route.set("/");
        }
      }),
      m("img", {
        src: "images/GoogleMaps.svg",
        height: "100%",
        "object-fit": "contain",
        style: {
          cursor: "pointer"
        },
        onclick: () => {
          m.route.set("/map");
        }
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
        value: FWM.searchText,
        onblur: (el) => {setTimeout(() => {el.target.focus()}, 10)},
        oninput: m.withAttr("value", (text) => {FWM.searchText = text;})
      })
      ),
      m("svg", {
        xmlns: "http://www.w3.org/2000/svg",
        viewBox: "-105 -105 210 210",
        style: {
          position: "absolute",
          top: "0",
          right: "0",
          width: "50px",
          height: "50px",
          cursor: "pointer"
        },
        onclick: () => {
          m.route.set("/options");
        }
      }, [
        m("line", {x1: "-60", y1: "-40", x2: "60", y2: "-40", stroke: "black", "stroke-width": "20"}),
        m("line", {x1: "-60", y1: "40", x2: "60", y2: "40", stroke: "black", "stroke-width": "20"})
      ])
    ];
  }
};

/* the single page app tying it all together */
var FWM = {
  searchText: "",
  position: {latitude: 61.87416667, longitude: 9.15472222},
  takeoffs: [], // DB.takeoffs with uninteresting entries filtered out

  oninit: () => {
    DB.favourited.addInitCallback(() => {
      m.redraw();
    });
    DB.takeoffs.addInitCallback(() => {
      FWM.updateTakeoffData();
    });
    if ("geolocation" in navigator) {
      navigator.geolocation.getCurrentPosition((position) => {
        FWM.position = position.coords;
        GoogleMap.moveTo({lat: FWM.position.latitude, lng: FWM.position.longitude});
        m.redraw();
      });
    }
  },

  view: (vnode) => {
    var layout = Layouts[FWM.layout];
    return [
      m("nav", FWM.getStyle("nav"), m(Nav)),
      m("main", [
        m("div", FWM.getStyle("takeoffList"), m(TakeoffList)),
        m("div", FWM.getStyle("options"), m(Options)),
        m("div", FWM.getStyle("googleMap"), m(GoogleMap)),
        m("div", FWM.getStyle("forecast"), m(Forecast))
      ])
    ];
  },

  layout: () => {
    return window.innerWidth >= 1000 ? "large" : "small";
  },

  getStyle: (name) => {
    var panels = Layouts[FWM.layout()].panels;
    for (var i = 0; i < panels.length; ++i) {
      var index = panels[i].panes.indexOf(name);
      if (index >= 0) {
        return {style: {...panels[i].style, ...{"display": (index == 0 ? "block" : "none")}}};
      }
    };
    console.log("No pane found for", name);
  },

  // update takeoff data
  updateTakeoffData: () => {
    var lastUpdated = DB.settings.get("last_updated", 0);
    fetch("/takeoffs?lastUpdated=" + lastUpdated)
    .then((response) => response.json())
    .then((data) => {
      var count = 0;
      for (i in data) {
        var takeoff = data[i];
        DB.takeoffs.set(takeoff.id, takeoff);
        ++count;
        if (takeoff.updated > lastUpdated) {
          lastUpdated = takeoff.updated;
        }
      }
      DB.settings.set("last_updated", lastUpdated);
      console.log("Updated takeoffs:", count);
      console.log("Last updated:", lastUpdated);
      FWM.updateTakeoffList();
    });
  },

  // update/init settings
  updateTakeoffList: () => {
    // update list of takeoffs we're to display in UI
    FWM.takeoffs = Object.values(DB.takeoffs.all())
      .filter((takeoff) => !(DB.settings.get("hide_missing_coords") && (takeoff.lat == 0.0 && takeoff.lng == 0.0)))
      .filter((takeoff) => !(DB.settings.get("hide_short_info") && (takeoff.name.length <= 3 || takeoff.desc.length <= 3)));
    // redraw map markers
    GoogleMap.updateMapMarkers();
    // redraw rest of ui
    m.redraw();
  },

  // convert text to html
  textToHtml: (text) => {
    if (!text) {
      return text;
    }
    // escape dangerous characters
    text = text.replace(/"/g, "&#34;").replace(/'/g, "&#39;").replace(/</g, "&#60;").replace(/>/g, "&#62;");
    // add links to urls
    text = text.replace(/(http:\/\/[^\s]+)/g, "<a href=\"\$1\" target=\"_blank\" onclick=\"event.stopPropagation();\">\$1</a>");
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
    DB.takeoffs.set(takeoff.id, takeoff);
    DB.favourited.set(takeoff.id, takeoff.favourite ? takeoff : null);
  },

  fetchMeteogram: (takeoff) => {
    Forecast.takeoff = takeoff;
    Forecast.loading = takeoff.id;
    fetch("/takeoffs/" + takeoff.id + "/meteogram")
      .then((response) => response.json())
      .then((data) => {
        // TODO: clear sounding if we asked for meteogram for another takeoff than currently displayed
        Forecast.loading = null;
        Forecast.images.meteogram = "data:image/gif;base64," + data.image;
        m.route.set("/forecast/" + takeoff.id);
        m.redraw();
      });
  },

  fetchSounding: (hours) => {
    Forecast.soundingHour = hours;
    var timestamp = new Date();
    timestamp.setUTCHours(hours);
    timestamp.setUTCMinutes(0);
    timestamp.setUTCSeconds(0);
    timestamp.setUTCMilliseconds(0);
    fetch("/takeoffs/" + Forecast.takeoff.id + "/sounding/" + timestamp.getTime())
      .then((response) => response.json())
      .then((data) => {
        Forecast.images.sounding = "data:image/gif;base64," + data[0].image;
        Forecast.images.theta = "data:image/gif;base64," + data[1].image;
        Forecast.images.text = "data:image/gif;base64," + data[2].image;
        m.route.set("/forecast/" + takeoff.id);
        m.redraw();
      });
  },

  calculateDistance: (lat1, lon1, lat2, lon2) => {
    var radlat1 = Math.PI * lat1 / 180;
    var radlat2 = Math.PI * lat2 / 180;
    var radtheta = Math.PI * (lon1 - lon2) / 180;
    var dist = Math.sin(radlat1) * Math.sin(radlat2) + Math.cos(radlat1) * Math.cos(radlat2) * Math.cos(radtheta);
    if (dist > 1) {
      dist = 1;
    }
    dist = Math.acos(dist) * 180 / Math.PI * 60 * 1.1515 * 1.609344;
    return dist;
  }
};

/* routing */
function route(names) {
  return {
    render: () => {
      Layouts[FWM.layout()].panels.forEach((panel) => {
        names.forEach((name) => {
          var index = panel.panes.indexOf(name);
          if (index >= 0) {
            panel.panes.splice(index, 1);
            panel.panes.splice(0, 0, name);
          }
        });
      });
      return m(FWM);
    }
  }
}

m.route(document.body, "/", {
  "/": route(["takeoffList", "googleMap"]),
  "/map": route(["googleMap", "takeoffList"]),
  "/options": route(["options", "googleMap"]),
  "/forecast/:takeoffId": route(["forecast", "takeoffList"])
})
