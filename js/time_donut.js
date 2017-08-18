/*
 * This file is part of the NoiseCapture application and OnoMap system.
 *
 * The 'OnoMaP' system is led by Lab-STICC and Ifsttar and generates noise maps via
 * citizen-contributed noise data.
 *
 * This application is co-funded by the ENERGIC-OD Project (European Network for
 * Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 * (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 * PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 * Community. The application work is also supported by the French geographic portal GEOPAL of the
 * Pays de la Loire region (http://www.geopal.org).
 *
 * Copyright (C) 2007-2016 - IFSTTAR - LAE
 * Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 * NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 * it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301  USA or see For more information,  write to Ifsttar,
 * 14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *  or write to scientific.computing@ifsttar.fr
 */

var TimeDonut = function(id, levels) {
  this.id = id;
  this.DONUT_LEVELS = [["_la50", "la50"], ["_laeq", "laeq"]];
  this.COLOR_RAMP = {
    0: "#FFFFFF",
    30: "#82A6AD",
    35: "#A0BABF",
    40: "#B8D6D1",
    45: "#CEE4CC",
    50: "#E2F2BF",
    55: "#F3C683",
    60: "#E87E4D",
    65: "#CD463E",
    70: "#A11A4D",
    75: "#75085C",
    80: "#430A4A"
  };
  this.levels = levels;
}

TimeDonut.prototype.getcolor = function(level) {
  var prev = -1;
  var i;
  for (i in this.COLOR_RAMP) {
    var n = parseInt(i);
    if ((prev != -1) && (level < n))
      return this.COLOR_RAMP[prev];
    else
      prev = n;
  }
  var levels = Object.keys(this.COLOR_RAMP);
  return this.COLOR_RAMP[levels[levels.length - 1]];
}

TimeDonut.prototype.getDonutFromSliceElement = function(sliceElement) {
    // Find if the element is referencing laeq or la50
    var parentElementId = sliceElement.parentElement.id;
    var id_cat = null;
    var full_id = null;
    for(let id_pair of this.DONUT_LEVELS) {
      var id_ext = id_pair[0];
      var pair_id_cat = id_pair[1];
      var pair_full_id = this.id+id_ext;
      if(parentElementId.startsWith(pair_full_id)) {
        return [pair_id_cat, pair_full_id];
      }
    }
    return [];
}

TimeDonut.prototype.hover = function(element) {
  if(typeof this.levels !== 'undefined') {
    var retVal = this.getDonutFromSliceElement(element);
    var id_cat = retVal[0];
    var full_id = retVal[1];
    var hour = parseInt(element.textContent);
    if(this.levels[hour] != null) {
      var value = this.levels[hour][id_cat];
      var centercircle = document.getElementById(full_id+"_centercircletext");
      var centercircletextunit = document.getElementById(full_id+"_centercircletextunit");
      centercircle.innerHTML = value.toFixed(1);
      centercircle.style.color = this.getcolor(value);
      centercircletextunit.innerHTML = "dB(A)";
    }
  }
}

TimeDonut.prototype.hoverOff = function(element) {
  var retVal = this.getDonutFromSliceElement(element);
  var id_cat = retVal[0];
  var full_id = retVal[1];
  var centercircle = document.getElementById(full_id+"_centercircletext");
  var centercircletextunit = document.getElementById(full_id+"_centercircletextunit");
  centercircle.innerHTML = "";
  centercircletextunit.innerHTML = "";
}

TimeDonut.prototype.loadLevels = function(levels) {
   this.levels = levels;
   for(let id_pair of this.DONUT_LEVELS) {
     var id_ext = id_pair[0];
     var id_cat = id_pair[1];
     var full_id = this.id+id_ext;
     var circleWeek = document.getElementById(full_id+"_ulcircle");
     for(child in circleWeek.children) {
       var el = circleWeek.children[child];
       if(el && el.className == "slice") {
         var hour = parseInt(el.children[0].textContent);
         if(typeof this.levels !== 'undefined' && this.levels[hour] != null) {
           var value = levels[hour][id_cat];
           el.style.backgroundColor = this.getcolor(value);
         } else {
           el.style.backgroundColor = 'white';
         }
       }
     }
   }
}

function initTimeDonut(id, levels) {
  var newDonut = new TimeDonut(id, levels);
  // id is the time period (week, saturday and sunday)
  // For each time period there is a specific level
  for(let id_pair of newDonut.DONUT_LEVELS) {
    var id_ext = id_pair[0];
    var id_cat = id_pair[1];
    var full_id = id+id_ext;
    var div_centercircletextunit = document.createElement('div');
    var div_centercircletext = document.createElement('div');
    var ul = document.createElement('ul');
    ul.setAttribute('id', full_id+'_ulcircle');
    ul.setAttribute('class', 'circle');
    for (sliceId = 0; sliceId < 24; sliceId++) {
      var li = document.createElement('li');
      li.onmouseenter = function(element) { newDonut.hover(element.target);};
      li.onmouseleave = function(element) { newDonut.hoverOff(element.target);};
      if(typeof levels !== 'undefined') {
        li.style.backgroundColor = newDonut.getcolor(levels[sliceId][id_cat]);
      }
      li.setAttribute('class', 'slice');
      var div = document.createElement('div');
      div.setAttribute('class', 'text');
      div.innerHTML = sliceId;
      li.appendChild(div);
      ul.appendChild(li);
    }
    var div_centercircle = document.createElement('div');
    div_centercircle.setAttribute('class', 'centercircle');
    div_centercircletext.setAttribute('class', 'centercircletext');
    div_centercircletext.setAttribute('id', full_id+'_centercircletext');
    div_centercircletextunit.setAttribute('class', 'centercircletextunit');
    div_centercircletextunit.setAttribute('id', full_id+'_centercircletextunit');
    var maindiv = document.getElementById(full_id);
    ul.appendChild(div_centercircle);
    div_centercircle.appendChild(div_centercircletext);
    div_centercircle.appendChild(div_centercircletextunit);
    maindiv.appendChild(ul);
  }
  return newDonut;
}
