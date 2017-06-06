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

TimeDonut.prototype.hover = function(element) {
  if(typeof this.levels !== 'undefined') {
    var hour = parseInt(element.textContent);
    var value = this.levels[hour].la50;
    var centercircle = document.getElementById(this.id+"_centercircletext");
    var centercircletextunit = document.getElementById(this.id+"_centercircletextunit");
    centercircle.innerHTML = Math.round(value);
    centercircle.style.color = this.getcolor(value);
    centercircletextunit.innerHTML = "dB(A)";
  }
}

TimeDonut.prototype.hoverOff = function(element) {
var centercircle = document.getElementById(this.id+"_centercircletext");
var centercircletextunit = document.getElementById(this.id+"_centercircletextunit");
  centercircle.innerHTML = "";
  centercircletextunit.innerHTML = "";
}

TimeDonut.prototype.loadLevels = function(levels) {
   this.levels = levels;
   var circleWeek = document.getElementById(this.id+"_ulcircle");
   for(child in circleWeek.children) {
     var el = circleWeek.children[child];
     if(el && el.className == "slice") {
       var hour = parseInt(el.children[0].textContent);
       if(typeof this.levels !== 'undefined' && this.levels[hour] != null) {
         var value = levels[hour].la50;
         el.style.backgroundColor = this.getcolor(value);
       } else {
         el.style.backgroundColor = 'white';
       }
     }
   }
}

function initTimeDonut(id, levels) {
  var div_centercircletextunit = document.createElement('div');
  var div_centercircletext = document.createElement('div');
  var newDonut = new TimeDonut(id, levels);
  var ul = document.createElement('ul');
  ul.setAttribute('id', id+'_ulcircle');
  ul.setAttribute('class', 'circle');
  for (sliceId = 0; sliceId < 24; sliceId++) {
    var li = document.createElement('li');
    li.onmouseenter = function(element) { newDonut.hover(element.target);};
    li.onmouseleave = function(element) { newDonut.hoverOff(element.target);};
    if(typeof levels !== 'undefined') {
      li.style.backgroundColor = newDonut.getcolor(levels[sliceId].la50);
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
  div_centercircletext.setAttribute('id', id+'_centercircletext');
  div_centercircletextunit.setAttribute('class', 'centercircletextunit');
  div_centercircletextunit.setAttribute('id', id+'_centercircletextunit');
  var maindiv = document.getElementById(id);
  ul.appendChild(div_centercircle);
  div_centercircle.appendChild(div_centercircletext);
  div_centercircle.appendChild(div_centercircletextunit);
  maindiv.appendChild(ul);
  return newDonut;
}
