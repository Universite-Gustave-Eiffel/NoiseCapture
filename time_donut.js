var TimeDonut = function(id) {
  this.id = id;
  this.COLOR_RAMP = {
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
}

TimeDonut.prototype.loadBackgroundColor = function() {
  var circleWeek = document.getElementById("circle_week");
  for (child in circleWeek.children) {
    var el = circleWeek.children[child];
    if (el && el.className == "slice") {
      var hour = parseInt(el.children[0].textContent);
      var value = getDummyLevelFromHour(hour);
      el.style.backgroundColor = getcolor(value);
    }
  }
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
}

TimeDonut.prototype.getDummyLevelFromHour = function(hour) {
  if (hour < 7) {
    return 45 + hour;
  } else if (hour > 20) {
    return 55 + hour - 20;
  } else {
    return 65 + hour - 6;
  }
}

TimeDonut.prototype.hover = function(element) {
  var hour = parseInt(element.textContent);
  var value = this.getDummyLevelFromHour(hour);
  var centercircle = document.getElementById(this.id+"_centercircletext");
  var centercircletextunit = document.getElementById(this.id+"_centercircletextunit");
  centercircle.innerHTML = Math.round(value);
  centercircle.style.color = this.getcolor(value);
  centercircletextunit.innerHTML = "dB(A)";
}

TimeDonut.prototype.hoverOff = function(element) {
var centercircle = document.getElementById(this.id+"_centercircletext");
var centercircletextunit = document.getElementById(this.id+"_centercircletextunit");
  centercircle.innerHTML = "";
  centercircletextunit.innerHTML = "";
}


function initTimeDonut(id) {
  var div_centercircletextunit = document.createElement('div');
  var div_centercircletext = document.createElement('div');
  var newDonut = new TimeDonut(id);
  var ul = document.createElement('ul');
  ul.setAttribute('class', 'circle');
  for (sliceId = 0; sliceId < 24; sliceId++) {
    var li = document.createElement('li');
    li.onmouseenter = function(element) { newDonut.hover(element.target);};
    li.onmouseleave = function(element) { newDonut.hoverOff(element.target);};
    li.setAttribute('class', 'slice');
    var div = document.createElement('div');
    div.setAttribute('class', 'text');
    div.innerHTML = sliceId + 1;
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
