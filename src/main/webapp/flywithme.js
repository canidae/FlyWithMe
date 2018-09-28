var DB = {
  set: (key, value) => {
    localStorage.setItem(key, value);
  },

  get: (key) => {
    return localStorage.getItem(key);
  },

  compress: (key, value) => {
    localStorage.setItem(key, LZString.compressToUTF16(value));
  },

  decompress: (key) => {
    return LZString.decompressFromUTF16(localStorage.getItem(key));
  }
}

var FWM = {
  googleMap: null,
  searchText: "",
  takeoffs: [],
  sortedTakeoffs: [],

  // get takeoff data, update if necessary
  updateTakeoffData: () => {
    var lastUpdated = DB.get("flightlog_last_updated") || 0;
    var now = new Date().getTime();
    FWM.takeoffs = JSON.parse(DB.decompress("flightlog_takeoffs") || "{}");
    if (Math.ceil((now - lastUpdated) / 86400000) > 5) {
      fetch("/takeoffs?lastUpdated=" + lastUpdated)
        .then(response => response.json())
        .then(data => {
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
          DB.compress("flightlog_takeoffs", JSON.stringify(FWM.takeoffs));
          DB.set("flightlog_last_updated", lastUpdated);
          FWM.sortTakeoffs();
        });
    }
  },

  // initialize and add the map
  initGoogleMap: () => {
    FWM.googleMap = new google.maps.Map(document.getElementById('google-map-view'), {zoom: 11, center: {lat: 61.87416667, lng: 9.15472222}, mapTypeId: 'terrain'});
    //var marker = new google.maps.Marker({position: rikssenter, map: FWM.googleMap});
    //FWM.googleMap.data.loadGeoJson('https://raw.githubusercontent.com/relet/pg-xc/master/geojson/luftrom.geojson');
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
    /* TODO: distance */
    } else {
      return ("" + a.name).localeCompare(b.name);
    }
  }
};

var takeoffListEntry = {
  view: (vnode) => {
    var takeoff = vnode.attrs.takeoff;
    return m("div", {id: takeoff.id, style: {
      position: "relative",
      height: "40px",
      cursor: "pointer",
      margin: "4px 0 4px 0",
      "background-color": vnode.attrs.index % 2 == 0 ? "#fff" : "#ddd"
    }, onclick: () => {FWM.panMap(takeoff)}, onmouseover: () => {}, onmouseout: () => {}}, [
      m("svg", {viewBox: "-105 -105 210 210", style: {
        position: "absolute",
        width: "38px",
        height: "38px"
      }}, [
        m("circle", {r: "104", stroke: "black", "stroke-width": "2", fill: "none"}),
        m("path", {visibility: (takeoff.wind & (1 << 7)) != 0 ? "visible" : "hidden", d: "M 0 0 L -38.268 -92.388 A 100 100 0 0 1  38.268 -92.388 Z", stroke: "none", fill: "#05c70f"}),
        m("path", {visibility: (takeoff.wind & (1 << 6)) != 0 ? "visible" : "hidden", d: "M 0 0 L  38.268 -92.388 A 100 100 0 0 1  92.388 -38.268 Z", stroke: "none", fill: "#05c70f"}),
        m("path", {visibility: (takeoff.wind & (1 << 5)) != 0 ? "visible" : "hidden", d: "M 0 0 L  92.388 -38.268 A 100 100 0 0 1  92.388  38.268 Z", stroke: "none", fill: "#05c70f"}),
        m("path", {visibility: (takeoff.wind & (1 << 4)) != 0 ? "visible" : "hidden", d: "M 0 0 L  92.388  38.268 A 100 100 0 0 1  38.268  92.388 Z", stroke: "none", fill: "#05c70f"}),
        m("path", {visibility: (takeoff.wind & (1 << 3)) != 0 ? "visible" : "hidden", d: "M 0 0 L  38.268  92.388 A 100 100 0 0 1 -38.268  92.388 Z", stroke: "none", fill: "#05c70f"}),
        m("path", {visibility: (takeoff.wind & (1 << 2)) != 0 ? "visible" : "hidden", d: "M 0 0 L -38.268  92.388 A 100 100 0 0 1 -92.388  38.268 Z", stroke: "none", fill: "#05c70f"}),
        m("path", {visibility: (takeoff.wind & (1 << 1)) != 0 ? "visible" : "hidden", d: "M 0 0 L -92.388  38.268 A 100 100 0 0 1 -92.388 -38.268 Z", stroke: "none", fill: "#05c70f"}),
        m("path", {visibility: (takeoff.wind & (1 << 0)) != 0 ? "visible" : "hidden", d: "M 0 0 L -92.388 -38.268 A 100 100 0 0 1 -38.268 -92.388 Z", stroke: "none", fill: "#05c70f"})
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
      }}, "Height: " + takeoff.alt),
      m("span", {style: {
        position: "absolute",
        top: "20px",
        left: "180px",
        width: "100px",
        height: "20px",
        "white-space": "nowrap",
        overflow: "hidden"
      }}, "Diff: " + takeoff.altdiff),
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
      }, src: "images/NOAA.svg", onclick: (e) => {e.stopPropagation();}}),
      m("div", {style: {
        position: "absolute",
        top: "50px",
        visibility: "hidden"
      }}, takeoff.description)
    ]);
  }
};

var takeoffList = {
  view: (vnode) => {
    return m("div", {style: {
        position: "absolute",
        top: "0",
        left: "0",
        bottom: "0",
        width: "500px",
        margin: "8px",
        overflow: "scroll",
        "overflow-x": "hidden"
    }}, [
      m("span", "Search:"),
      m("input", {onblur: (el) => {setTimeout(() => {el.target.focus()}, 10)}, oninput: m.withAttr("value", (text) => {FWM.searchText = text; FWM.sortTakeoffs();}), value: FWM.searchText}),
      m("br"),
      m("div", FWM.sortedTakeoffs.map((takeoff, index) => {
        return m(takeoffListEntry, {key: takeoff.id, takeoff: takeoff, index: index})
      }))
    ]);
  }
};

var main = {
  oninit: (vnode) => {
    FWM.updateTakeoffData();
    FWM.sortTakeoffs();
  },

  view: () => {
    return m("main", [
      m(takeoffList),
      m("div", {id: "google-map-view", style: {
        position: "absolute",
        top: "0",
        left: "500px",
        right: "0",
        bottom: "0",
        margin: "8px"
      }})
    ]);
  },
};

// mount main
m.mount(document.body, main);
