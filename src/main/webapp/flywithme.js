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

/* keeping track of data and user settings */
var DB = JSON.parse(LZString.decompressFromUTF16(localStorage.getItem("FWM")) || "{}");

/* logic for FWM */
var FWM = {
  googleMap: null,
  infoWindow: null,
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

  // save data/settings
  save: () => {
    if (FWM._idleCallbackId) {
      window.cancelIdleCallback(FWM._idleCallbackId);
    }
    window.requestIdleCallback(() => {
      localStorage.setItem("FWM", LZString.compressToUTF16(JSON.stringify(DB)));
      FWM._idleCallbackId = null;
    });
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

      /* TODO: below should not be here */
      FWM.sortTakeoffs();
      var markers = Object.values(DB.takeoffs).map((takeoff) => {
        FWM.infoWindow = new google.maps.InfoWindow({});
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
          FWM.infoWindow.setContent("<div><h1>" + takeoff.name + takeoffExitsHtml + "</h1><p>" + FWM.textToHtml(takeoff.desc) + "</p></div>");
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
    FWM.sortTakeoffs();
    FWM.save();
  },

  fetchMeteogram: (takeoff) => {
    FWM.active.forecast.takeoff = takeoff;
    FWM.active.forecast.loading = takeoff.id;
    fetch("/takeoffs/" + takeoff.id + "/meteogram")
      .then((response) => response.json())
      .then((data) => {
        FWM.active.forecast.meteogram = "data:image/gif;base64," + data.image;
        FWM.active.forecast.loading = null;
        m.redraw();
      });
  },

  fetchSounding: (hours) => {
    FWM.active.forecast.soundingHour = hours;
    var timestamp = new Date();
    timestamp.setUTCHours(hours);
    timestamp.setUTCMinutes(0);
    timestamp.setUTCSeconds(0);
    timestamp.setUTCMilliseconds(0);
    fetch("/takeoffs/" + FWM.active.forecast.takeoff.id + "/sounding/" + timestamp.getTime())
      .then((response) => response.json())
      .then((data) => {
        FWM.active.forecast.sounding = "data:image/gif;base64," + data[0].image;
        FWM.active.forecast.theta = "data:image/gif;base64," + data[1].image;
        FWM.active.forecast.text = "data:image/gif;base64," + data[2].image;
        m.redraw();
      });
  },

  sortTakeoffs: () => {
    FWM.active.sortedTakeoffs = Object.values(DB.takeoffs || {})
      .filter((takeoff) => takeoff.name.match(new RegExp(FWM.active.searchText, "i")))
      .filter((takeoff) => takeoff.lat != 0.0 || takeoff.lng != 0.0)
      .filter((takeoff) => takeoff.name.length > 3)
      .filter((takeoff) => takeoff.desc.length > 3)
      .sort(FWM.takeoffSortComparator).slice(0, 20);
    m.redraw();
  },

  // sort takeoff based on favouritability, distance & name
  takeoffSortComparator: (a, b) => {
    if (a.favourite && !b.favourite) {
      return -1;
    } else if (!a.favourite && b.favourite) {
      return 1;
    } else if (FWM.position && FWM.position.latitude && FWM.position.longitude) {
      return FWM.calculateDistance(FWM.position.latitude, FWM.position.longitude, a.lat, a.lng) - FWM.calculateDistance(FWM.position.latitude, FWM.position.longitude, b.lat, b.lng);
    } else {
      return ("" + a.name).localeCompare(b.name);
    }
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
        animation: FWM.active.forecast.loading == takeoff.id ? "loading 2s infinite" : null,
        cursor: "pointer"
      }, src: "images/NOAA.svg", onclick: (e) => {FWM.fetchMeteogram(takeoff); e.stopPropagation();}}),
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
        onclick: () => FWM.active.forecast = {}
      }, "Close"),
      m("select", {
        selectedIndex: FWM.active.forecast.soundingHour ? (FWM.active.forecast.soundingHour - 9) / 3 : 0,
        onchange: m.withAttr("selectedIndex", (index) => {FWM.fetchSounding(index * 3 + 9);})
      }, forecastOptions),
      m("br"),
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
  oninit: (vnode) => {
    FWM.updateTakeoffData();
    FWM.sortTakeoffs();
    if ("geolocation" in navigator) {
      navigator.geolocation.getCurrentPosition((position) => {
        FWM.position = position.coords;
        FWM.googleMap.panTo({lat: FWM.position.latitude, lng: FWM.position.longitude});
        FWM.sortTakeoffs();
      });
    }
  },

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
