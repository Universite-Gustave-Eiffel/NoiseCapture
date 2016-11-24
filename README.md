# About NoiseCapture App

[![Build Status](https://travis-ci.org/Ifsttar/NoiseCapture.svg?branch=master)](https://travis-ci.org/Ifsttar/NoiseCapture) 

NoiseCapture App is Android App dedicated to the measurement of environmental noise.
The development of NoiseCapture is still in progress: the present application is experimental.

## Description
NoiseCapture App is an Android App project for measuring environmental noise using a smartphone. The goal is to produce relevant noise indicators from audio processing, including a geospatial representation. Measurements can be shared with the community in order to produce participatory noise maps. NoiseCapture App is a part of global infrastructure, _i.e._ a Spatial Data Infrastructure (SDI), called the OnoM@p SDI, that allows to process and represent the geospatial information.

* A full description of the whole OnoM@p SDI, including the NoiseCapture App, is given in the [wiki pages](https://github.com/Ifsttar/NoiseCapture/wiki).
* A user guide, for the use of the NoiseCapture App, is proposed within the NoiseCapture App (see the 'Help' page in the menu of NoiseCapture App). This user guide can also be displyed [here as an html page](https://raw.githubusercontent.com/Ifsttar/NoiseCapture/e110b3170edc6a60e585d3dd78380d80c82366e2/app/src/main/assets/html/help.html).

## Features

NoiseCapture App features are divided into 3 parts:

 - Measurement - Once the sound level calibration is done, the user start the measurement in order to record each second the LAeq, an average sound energy over a period of 1s. The spectrum repartition of the sound are analysed and stored using the Fourrier transform. The device location are recorded while measuring the sound level. The user has the hability to provide his own feedback about the feeling of the noise environment.

 - Extented report - Advanced statistics are computed locally on the phone and shown to the user. For each user's measurement the locations of the noise levels are displayed in a map.

 - Share results with the community - Anonymous results are transfered to Virtual Hubs (web server) and post-processed in order to build a noise map that merge all community results.

## Developments
NoiseCapture App is a collaboration between the [Environmental Acoustic Laboratory](http://www.lae.ifsttar.fr/en/) ([Ifsttar](http://www.ifsttar.fr)) and the [Lab-STICC](http://www.lab-sticc.fr/) CNRS.

## Funding
This application was developed under the initial funding the European project [ENERGIC-OD](http://www.energic-od.eu/), with the help of the [GEOPAL](http://www.geopal.org/accueil) program.

## Licence
NoiseCapture is released under the GENERAL PUBLIC LICENSE Version 3. Please refer to GPLv3 for more details.

