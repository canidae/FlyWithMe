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
  searchText: "",
  takeoffs: [],
  sortedTakeoffs: [],
  forecast: {},
  css: {
    nav: {
      top: "0",
      left: "0",
      right: "0",
      height: "50px",
      background: "lightskyblue"
    },
    takeoffList: {
        position: "absolute",
        top: "50px",
        left: "0",
        bottom: "0",
        width: "500px",
        margin: "8px",
        overflow: "scroll",
        "overflow-x": "hidden"
    },
    takeoff: {

    },
    forecast: {
      position: "absolute",
      top: "50px",
      left: "500px",
      height: "400px",
      right: "0",
      margin: "8px"
    },
    googleMap: {
      position: "absolute",
      top: "450px",
      left: "500px",
      right: "0",
      bottom: "0",
      margin: "8px"
    }
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
        });
    }
  },

  // convert text to html
  textToHtml: (text) => {
    // escape dangerous characters
    text = text.replace(/"/g, "&#34;").replace(/'/g, "&#39;").replace(/</g, "&#60;").replace(/>/g, "&#62;");
    // replace newlines with <br />
    text = text.replace(/\n/g, "<br />");
    // add links to urls
    text = text.replace(/(http:\/\/[^\s]+)/g, "<a href=\"\$1\">\$1</a>");
    return text;
  },

  // pan map to position
  panMap: (position) => {
    console.log("Pan map to:", position);
    FWM.googleMap.panTo(position);
    console.log(FWM.googleMap.getBounds().getNorthEast().lat());
  },

  // expand takeoff details
  showDetails: (el, takeoff) => {
    console.log(el, takeoff);
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
        FWM.forecast.meteogram = "data:image/gif;base64," + data.image;
        m.redraw();
      });
  },

  fetchSounding: (takeoff, timestamp) => {
    fetch("/takeoffs/" + takeoff.id + "/sounding/" + timestamp)
      .then((response) => response.json())
      .then((data) => {
        FWM.forecast.sounding = "data:image/gif;base64," + data[0].image;
        FWM.forecast.theta = "data:image/gif;base64," + data[1].image;
        FWM.forecast.text = "data:image/gif;base64," + data[2].image;
        m.redraw();
      });
  },

  sortTakeoffs: () => {
    FWM.sortedTakeoffs = Object.values(FWM.takeoffs).filter((takeoff) => takeoff.name.match(new RegExp(FWM.searchText, "i"))).sort(FWM.takeoffSortComparator).slice(0, 20);
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
      m("svg", {viewBox: "-105 -105 210 210", style: {
        position: "absolute",
        width: "38px",
        height: "38px"
      }}, [
        m("circle", {r: "104", stroke: "black", "stroke-width": "2", fill: "none"}),
        m("path", {visibility: (takeoff.exits & (1 << 7)) != 0 ? "visible" : "hidden", d: "M 0 0 L -38.268 -92.388 A 100 100 0 0 1  38.268 -92.388 Z", stroke: "none", fill: "#05c70f"}),
        m("path", {visibility: (takeoff.exits & (1 << 6)) != 0 ? "visible" : "hidden", d: "M 0 0 L  38.268 -92.388 A 100 100 0 0 1  92.388 -38.268 Z", stroke: "none", fill: "#05c70f"}),
        m("path", {visibility: (takeoff.exits & (1 << 5)) != 0 ? "visible" : "hidden", d: "M 0 0 L  92.388 -38.268 A 100 100 0 0 1  92.388  38.268 Z", stroke: "none", fill: "#05c70f"}),
        m("path", {visibility: (takeoff.exits & (1 << 4)) != 0 ? "visible" : "hidden", d: "M 0 0 L  92.388  38.268 A 100 100 0 0 1  38.268  92.388 Z", stroke: "none", fill: "#05c70f"}),
        m("path", {visibility: (takeoff.exits & (1 << 3)) != 0 ? "visible" : "hidden", d: "M 0 0 L  38.268  92.388 A 100 100 0 0 1 -38.268  92.388 Z", stroke: "none", fill: "#05c70f"}),
        m("path", {visibility: (takeoff.exits & (1 << 2)) != 0 ? "visible" : "hidden", d: "M 0 0 L -38.268  92.388 A 100 100 0 0 1 -92.388  38.268 Z", stroke: "none", fill: "#05c70f"}),
        m("path", {visibility: (takeoff.exits & (1 << 1)) != 0 ? "visible" : "hidden", d: "M 0 0 L -92.388  38.268 A 100 100 0 0 1 -92.388 -38.268 Z", stroke: "none", fill: "#05c70f"}),
        m("path", {visibility: (takeoff.exits & (1 << 0)) != 0 ? "visible" : "hidden", d: "M 0 0 L -92.388 -38.268 A 100 100 0 0 1 -38.268 -92.388 Z", stroke: "none", fill: "#05c70f"})
      ]),
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
      m("svg", {style: {
        position: "absolute",
        right: "91px",
        width: "38px",
        height: "38px",
        cursor: "pointer"
      }, onclick: (e) => {FWM.showDetails(this, takeoff); e.stopPropagation();}, viewBox: "-105 -105 210 210"}, [
        m("path", {d: "M -80 -30 L 0 30 L 80 -30", stroke: "black", "stroke-width": "16", fill: "none"})
      ]),
      m("svg", {style: {
        position: "absolute",
        right: "48px",
        width: "38px",
        height: "38px",
        cursor: "pointer"
      }, viewBox: "0 0 51 48", onclick: (e) => {FWM.toggleFavourite(takeoff); e.stopPropagation();}}, [
        m("path", {fill: takeoff.favourite ? "yellow" : "none", stroke: "#000", d: "m25,1 6,17h18l-14,11 5,17-15-10-15,10 5-17-14-11h18z"})
      ]),
      m("img", {style: {
        position: "absolute",
        right: "5px",
        width: "38px",
        height: "38px",
        cursor: "pointer"
      }, src: "images/NOAA.svg", onclick: (e) => {FWM.fetchMeteogram(takeoff); FWM.fetchSounding(takeoff, new Date().getTime() + 10800000); e.stopPropagation();}})
    ];
  }
};

var takeoffListView = {
  view: (vnode) => {
    return [
      m("span", "Search:"),
      m("input", {onblur: (el) => {setTimeout(() => {el.target.focus()}, 10)}, oninput: m.withAttr("value", (text) => {FWM.searchText = text; FWM.sortTakeoffs();}), value: FWM.searchText}),
      m("br"),
      m("div", FWM.sortedTakeoffs.map((takeoff, index) => {
        return m("div", {id: takeoff.id, key: takeoff.id, style: {
          position: "relative",
          height: "40px",
          cursor: "pointer",
          margin: "4px 0 4px 0",
          "background-color": index % 2 == 0 ? "#fff" : "#ddd"
        }, onclick: () => {FWM.panMap(takeoff)}}, m(takeoffListEntry, {takeoff: takeoff, index: index}));
      }))
    ];
  }
};

var takeoffView = {
  view: (vnode) => {
    return [
      m("div", {style: {
      }}),
      m("div", {style: {
      }}),
      m("div", {style: {
      }})
    ];
  }
};

var forecastView = {
  view: (vnode) => {
    return [
      m("img", {
        name: "meteogram",
        height: "100%",
        width: "25%",
        "object-fit": "contain",
        src: FWM.forecast.meteogram
      }),
      m("img", {
        name: "sounding",
        height: "100%",
        width: "25%",
        "object-fit": "contain",
        src: FWM.forecast.sounding
      }),
      m("img", {
        name: "theta",
        height: "100%",
        width: "25%",
        "object-fit": "contain",
        src: FWM.forecast.theta
      }),
      m("img", {
        name: "text",
        height: "100%",
        width: "25%",
        "object-fit": "contain",
        src: FWM.forecast.text
      })
    ];
  }
};

var googleMapView = {
  oncreate: (vnode) => {
    FWM.googleMap = new google.maps.Map(vnode.dom, {zoom: 11, center: {lat: 61.87416667, lng: 9.15472222}, mapTypeId: 'terrain'});
    //var marker = new google.maps.Marker({position: rikssenter, map: FWM.googleMap});
    //FWM.googleMap.data.loadGeoJson('https://raw.githubusercontent.com/relet/pg-xc/master/geojson/luftrom.geojson');
  },

  view: (vnode) => {
    return m("div", {id: "google-map-view", style: {height: "100%"}});
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
      })
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
      m("div", {style: FWM.css.takeoffList}, m(takeoffListView)),
      m("div", {style: FWM.css.takeoff}, m(takeoffView)),
      m("div", {style: FWM.css.forecast}, m(forecastView)),
      m("div", {style: FWM.css.googleMap}, m(googleMapView))
    ];
  },
};

var body = {
  view: (vnode) => {
    return [
      m("nav", {style: FWM.css.nav}, m(nav)),
      m("main", m(main))
    ];
  }
}

// mount main
m.mount(document.body, body);
