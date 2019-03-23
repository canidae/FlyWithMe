/* shims for ancient browsers */
window.requestIdleCallback = window.requestIdleCallback || ((cb) => {
  var start = Date.now();
  return setTimeout(() => {
    cb({
      didTimeout: false,
      timeRemaining: () => {
        return Math.max(0, 50 - (Date.now() - start));
      }
    });
  }, 1);
});

window.cancelIdleCallback = window.cancelIdleCallback || ((id) => {
  clearTimeout(id);
});

/* event listeners */
window.addEventListener("resize", function() {
  if (window.innerWidth >= 1400) {
    FWM.layout = "large";
  } else {
    FWM.layout = "small";
  }
});

/* data we want to persist */
var DB = JSON.parse(LZString.decompressFromUTF16(localStorage.getItem("FWM")) || "{}");

/* themes */
var themes = {
  white: {
    background: "white"
  }
};

/* layouts */
var layouts = {
  large: {
    panes: {
      top: {
        style: {
          position: "absolute",
          top: "0",
          left: "0",
          height: "50px",
          width: "500px"
        },
        paneStack: ["nav"]
      },
      left: {
        style: {
          position: "absolute",
          top: "50px",
          left: "0",
          bottom: "0",
          width: "500px"
        },
        paneStack: ["takeoffList", "options"]
      },
      right: {
        style: {
          position: "absolute",
          top: "0",
          left: "500px",
          right: "0",
          bottom: "0"
        },
        paneStack: ["googleMap", "forecast"]
      }
    }
  },
  small: {
    // TODO
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
      }, src: "images/navigate.svg"}),
      m("svg", {xmlns: "http://www.w3.org/2000/svg", style: {
        position: "absolute",
        right: "50px",
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
        animation: Forecast.loading == takeoff.id ? "loading 2s infinite" : null,
        cursor: "pointer"
      }, src: "images/NOAA.svg", onclick: (e) => {FWM.fetchMeteogram(takeoff); e.stopPropagation();}}),
      m("div", {style: {
        position: "relative",
        display: FWM.takeoff.id == takeoff.id ? "block" : "none"
      }}, m.trust(FWM.textToHtml(takeoff.desc)))
    ];
  }
};

/* list of takeoffs */
var TakeoffList = {
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
    return FWM.takeoffs.filter((takeoff) => takeoff.name.match(new RegExp(FWM.searchText, "i"))).sort(comparator).slice(0, 20).map((takeoff, index) => {
      return m("div", {id: takeoff.id, key: takeoff.id, style: {
        position: "relative",
        cursor: "pointer",
        "background-color": index % 2 == 0 ? "#fff" : "#ddd"
      }, onclick: () => {
        if (FWM.takeoff.id == takeoff.id) {
          GoogleMap.map.panTo(FWM.prevMapCenter);
          GoogleMap.map.setZoom(FWM.prevMapZoom);
          FWM.takeoff = {};
        } else {
          if (FWM.takeoff.id == null) {
            FWM.prevMapCenter = GoogleMap.map.getCenter();
            FWM.prevMapZoom = GoogleMap.map.getZoom();
          }
          FWM.takeoff = takeoff;
          // TODO (not just here): don't access GoogleMap.map directly, create methods instead (which also checks that GoogleMap.map is valid)
          GoogleMap.map.panTo(takeoff);
          GoogleMap.map.setZoom(14);
        }
      }}, m(TakeoffListEntry, {takeoff: takeoff}));
    });
  }
};

/* map view of takeoffs */
var GoogleMap = {
  map: null,
  infoWindow: null,
  markerClusterer: null,

  oncreate: (vnode) => {
    GoogleMap.map = new google.maps.Map(vnode.dom, {zoom: 11, center: {lat: FWM.position.latitude, lng: FWM.position.longitude}, mapTypeId: 'terrain'});

    // TODO: show airspace
    //GoogleMap.map.data.loadGeoJson('https://raw.githubusercontent.com/relet/pg-xc/master/geojson/luftrom.geojson');
    
    // show takeoff map markers
    GoogleMap.updateMapMarkers();
  },

  view: (vnode) => {
    return m("div", {id: "google-map-view", style: {height: "100%"}});
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
      m("button", {
        onclick: () => Forecast.images = {}
      }, "Close"),
      m("select", {
        selectedIndex: Forecast.soundingHour ? (Forecast.soundingHour - 9) / 3 : 0,
        onchange: m.withAttr("selectedIndex", (index) => {FWM.fetchSounding(index * 3 + 9);})
      }, forecastOptions),
      m("br"),
      m("img", {
        name: "meteogram",
        src: Forecast.images.meteogram
      }),
      m("img", {
        name: "sounding",
        src: Forecast.images.sounding
      }),
      m("img", {
        name: "theta",
        src: Forecast.images.theta
      }),
      m("img", {
        name: "text",
        src: Forecast.images.text
      })
    ];
  }
};

/* user settings */
var Options = {
  view: (vnode) => {
    return [
      m("h1", "Takeoff filters"),
      m("input[type=checkbox]", {id: "hide_missing_coords", checked: DB.hide_missing_coords, onclick: m.withAttr("checked", () => {
        DB.hide_missing_coords = !DB.hide_missing_coords;
        FWM.updateSettings();
      })}),
      m("label", {"for": "hide_missing_coords"}, "Hide takeoffs with missing coordinates"),
      m("br"),
      m("input[type=checkbox]", {id: "hide_short_info", checked: DB.hide_short_info, onclick: m.withAttr("checked", () => {
        DB.hide_short_info = !DB.hide_short_info;
        FWM.updateSettings();
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
          FWM.setWindowVisibility("takeoffList", true);
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
          FWM.setWindowVisibility("googleMap", true);
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
          FWM.setWindowVisibility("options", true);
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
  theme: "white",
  layout: "large",
  searchText: "",
  position: {latitude: 61.87416667, longitude: 9.15472222},
  takeoffs: [], // DB.takeoffs with uninteresting entries filtered out
  takeoff: {}, // selected takeoff

  // TODO: move this to Forecast
  forecast: {}, // current requested/displayed forecast

  oninit: (vnode) => {
    FWM.updateTakeoffData();
    FWM.updateSettings();
    if ("geolocation" in navigator) {
      navigator.geolocation.getCurrentPosition((position) => {
        FWM.position = position.coords;
        GoogleMap.map.panTo({lat: FWM.position.latitude, lng: FWM.position.longitude});
        m.redraw();
      });
    }
  },

  view: (vnode) => {
    return [
      m("nav", FWM.getPaneStyle("nav"), m(Nav)),
      m("main", [
        m("div", FWM.getPaneStyle("options"), m(Options)),
        m("div", FWM.getPaneStyle("forecast"), m(Forecast)),
        m("div", FWM.getPaneStyle("googleMap"), m(GoogleMap)),
        m("div", FWM.getPaneStyle("takeoffList"), m(TakeoffList))
      ])
    ];
  },

  // save data/settings
  save: () => {
    // wait a short time, attempting to avoid freezing ui when user is active
    setTimeout(() => {
      if (FWM._idleCallbackId) {
        window.cancelIdleCallback(FWM._idleCallbackId);
      }
      FWM._idleCallbackId = window.requestIdleCallback(() => {
        localStorage.setItem("FWM", LZString.compressToUTF16(JSON.stringify(DB)));
        FWM._idleCallbackId = null;
      });
    }, 3000);
  },

  setWindowVisibility(name, visible) {
    Object.entries(layouts[FWM.layout].panes).forEach(([paneName, pane]) => {
      var index = pane.paneStack.indexOf(name);
      if (index >= 0) {
        pane.paneStack.splice(index, 1);
        pane.paneStack.splice(visible ? 0 : pane.paneStack.length, 0, name);
      }
    });
  },

  getPaneStyle(name) {
    var paneKeys = Object.keys(layouts[FWM.layout].panes);
    for (var i = 0; i < paneKeys.length; ++i) {
      var pane = layouts[FWM.layout].panes[paneKeys[i]];
      var index = pane.paneStack.indexOf(name);
      if (index >= 0) {
        return {style: {...pane.style, ...themes[FWM.theme], ...{"display": (index == 0 ? "block" : "none")}}};
      }
    }
  },

  // update takeoff data
  updateTakeoffData: () => {
    if (!DB.takeoffs) {
      DB.takeoffs = {};
    }
    if (!DB.lastUpdated) {
      DB.lastUpdated = 0;
    }
    var now = new Date().getTime();
    fetch("/takeoffs?lastUpdated=" + DB.lastUpdated)
    .then((response) => response.json())
    .then((data) => {
      var count = 0;
      for (i in data) {
        var takeoff = data[i];
        DB.takeoffs[takeoff.id] = takeoff;
        ++count;
        if (takeoff.updated > DB.lastUpdated) {
          DB.lastUpdated = takeoff.updated
        }
      }
      console.log("Updated takeoffs:", count);
      FWM.save();
    });
  },

  // update/init settings
  updateSettings: () => {
    // set default values of undefined settings
    if (DB.hide_missing_coords == undefined) {
      DB.hide_missing_coords = true;
    }
    if (DB.hide_short_info == undefined) {
      DB.hide_short_info = true;
    }
    // update list of takeoffs we're to display in UI
    FWM.takeoffs = Object.values(DB.takeoffs || {})
      .filter((takeoff) => !(DB.hide_missing_coords && (takeoff.lat == 0.0 && takeoff.lng == 0.0)))
      .filter((takeoff) => !(DB.hide_short_info && (takeoff.name.length <= 3 || takeoff.desc.length <= 3)));
    // redraw map markers
    GoogleMap.updateMapMarkers();
    // save
    FWM.save();
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
    var takeoffs = DB.takeoffs || {};
    takeoffs[takeoff.id].favourite = !takeoffs[takeoff.id].favourite;
    FWM.save();
  },

  fetchMeteogram: (takeoff) => {
    Forecast.takeoff = takeoff;
    Forecast.loading = takeoff.id;
    fetch("/takeoffs/" + takeoff.id + "/meteogram")
      .then((response) => response.json())
      .then((data) => {
        Forecast.images.meteogram = "data:image/gif;base64," + data.image;
        Forecast.loading = null;
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

// mount app
m.mount(document.body, FWM);
