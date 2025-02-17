DROP TABLE IF EXISTS NOISECAPTURE_FREQ, NOISECAPTURE_POINT, NOISECAPTURE_TRACK, NOISECAPTURE_USER,
  NOISECAPTURE_TAG, NOISECAPTURE_TRACK_TAG, NOISECAPTURE_AREA, NOISECAPTURE_PROCESS_QUEUE,
  NOISECAPTURE_AREA_PROFILE, NOISECAPTURE_AREA_CLUSTER, NOISECAPTURE_PARTY, NOISECAPTURE_STATS_LAST_TRACKS, NOISECAPTURE_DUMP_TRACK_ENVELOPE;

 -- H2 Only
 CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP;

-- tables

-- Table: NOISECAPTURE_USER
CREATE TABLE NOISECAPTURE_USER (
    PK_USER serial  NOT NULL,
    USER_UUID char(36)  NOT NULL,
    PSEUDO text,
    DATE_CREATION date  NOT NULL,
    PROFILE varchar default '',
    CONSTRAINT NOISECAPTURE_USER_PK PRIMARY KEY (PK_USER)
);

COMMENT ON COLUMN NOISECAPTURE_USER.PROFILE IS 'User acoustic knowledge, one of NONE, NOVICE, EXPERT';

-- Table: NOISECAPTURE_PARTY
CREATE TABLE NOISECAPTURE_PARTY (
    PK_PARTY serial NOT NULL,
    THE_GEOM geometry NOT NULL,
    LAYER_NAME varchar UNIQUE NOT NULL,
    TITLE varchar NOT NULL,
    TAG varchar NOT NULL,
    DESCRIPTION varchar NOT NULL,
    START_TIME TIMESTAMPTZ,
    END_TIME TIMESTAMPTZ,
    FILTER_TIME boolean NOT NULL default false,
    FILTER_AREA boolean NOT NULL default false,
    CONSTRAINT NOISECAPTURE_PARTY_PK PRIMARY KEY (PK_PARTY)
);

COMMENT ON COLUMN NOISECAPTURE_PARTY.title IS 'Short NoiseParty title';
COMMENT ON COLUMN NOISECAPTURE_PARTY.description IS 'Long description of the NoiseParty';
COMMENT ON COLUMN NOISECAPTURE_PARTY.tag IS 'Tag typed by users';
COMMENT ON COLUMN NOISECAPTURE_PARTY.the_geom IS 'NoiseParty location';
COMMENT ON COLUMN NOISECAPTURE_PARTY.layer_name IS 'Layer name in leaflet url, must be unique';
COMMENT ON COLUMN NOISECAPTURE_PARTY.filter_time IS 'If enabled, reject track with time out of start_time end_time range';
COMMENT ON COLUMN NOISECAPTURE_PARTY.filter_area IS 'If enabled, reject track that does not intersects with the_geom';

-- NoiseCapture party data POSTGIS only
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('SRID=4326;POLYGON ((-1.9387643337249756 46.860080718994254, -1.9387643337249756 47.393703460693416, -1.1155385971069336 47.393703460693416, -1.1155385971069336 46.860080718994254, -1.9387643337249756 46.860080718994254))','TEST44','Test noisecapture party','TEST44','NoiseCapture party for test purposes','2018-03-17 02:00:00+01','2030-03-26 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('SRID=4326;POLYGON ((-2.668678045272827 47.68943405151367, -2.668678045272827 47.773105621338004, -2.52265286445612 47.773105621338004, -2.52265286445612 47.68943405151367, -2.668678045272827 47.68943405151367))','PNRGM_Elven','FestNoise - Elven','PNRGM','Le Parc Naturel Régional du Golfe du Morbihan s''intéresse à l''effet du bruit sur la biodiversité. Nous vous proposons de venir arpenter les chemins d''Elven avec nous pour mesurer le bruit autour de vous !Armé de votre smartphone Android et de l''application NoiseCapture, les mesures serviront à évaluer à quel point les espaces naturels sont impactés par la pollution sonore.','2018-06-08 03:00:00+02','2018-06-16 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-2.34041 47.25688, -2.34041 47.26488, -2.33241 47.26488, -2.33241 47.25688, -2.34041 47.25688))','SNDIGITALWEEK','Digital Week 2017 Pornichet','SNDIGITALWEEK','<p>La Ville de Pornichet s''associe à la Saint-Nazaire Digital Week le mercredi 20 septembre, et propose de nombreuses animations gratuites et ouvertes à tous dédiées au numérique à l''hippodrome.</p><p>Venez contribuer à la création d''une carte du bruit participative, en temps réel sur les territoires de la CARENE / CAP ATLANTIQUE grâce à l''utilisation d''une application smartphone : Noise Capture.</p>',NULL,NULL,false,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-8.427327052129748 43.32281759490422, -8.427327052129748 43.35376150253824, -8.376441574368796 43.35376150253824, -8.376441574368796 43.32281759490422, -8.427327052129748 43.32281759490422))','UDC','Universidade da Coruña','UDC','This map belongs to the EDUCATION FOR SUSTAINABILITY project of Universidade da Coruña (http://www.udc.es; https://www.udc.es/sociedade/medio_ambiente/curso/) whose objective is to improve the training of the university community and citizenship in the basic concepts of environmental and social sustainability, and develop their capacity to act on those issues that are nowadays priorities (pollution, waste, climate change, resources, health...).','2018-04-17 03:00:00+02','2018-12-31 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((4.831 52.3495, 4.831 52.3951, 4.9712 52.3951, 4.9712 52.3495, 4.831 52.3495))','AMSOUNDS','Amsterdam Sounds: noise in the city','AMSOUNDS','How do you experience sound in your neighbourhood: the café around the corner, your neighbours or the nearby traffic? Or are you curious about the noise level you are exposed to during a festival? During this workshop we research noise and noise pollution, explore measuring strategies and use sensors to measure noise. Together we map sound in the city to then explore solutions and the possibilities of setting up a citizens’ measuring network.','2018-06-20 03:00:00+02','2018-09-21 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('SRID=4326;POLYGON ((-2.9296572208403973 47.62964630126953, -2.9296572208403973 47.68764877319347, -2.8554441928863525 47.68764877319347, -2.8554441928863525 47.62964630126953, -2.9296572208403973 47.62964630126953))','PNRGM_Plougoumelen','NoiseParty - Plougoumelen','PNRGM','Le Parc Naturel Régional du Golfe du Morbihan s''intéresse à l''effet du bruit sur la biodiversité. Nous vous proposons de venir arpenter les chemins de Plougoumelen (56) avec nous pour mesurer le bruit autour de vous ! Armé de votre smartphone Android et de l''application NoiseCapture, les mesures serviront à évaluer à quel point les espaces naturels sont impactés par la pollution sonore.','2018-07-18 03:00:00+02','2018-09-01 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-1.5623116493225098 47.20940916521795, -1.5531492233276367 47.20940916521795, -1.5531492233276367 47.20388432979386, -1.5623116493225098 47.20388432979386, -1.5623116493225098 47.20940916521795))','FDS2017','Fête de la science 2017','FDS2017','<p>Rendez-vous au Village des Sciences du 13 au 15 octobre 2017 à l''Ecole d''Architecture de Nantes. L''Amicale des doctorants de l''Ifsttar (Adin) vous fait découvrir des travaux de recherche innovants ! Profitez de l''événement avec le code #FDS2017 pour contribuez à la carte de bruit de la Région Nantaise. RDV au stand de l''Adin pour plus d''informations.</p>','2017-10-12 03:00:00+02','2017-10-16 03:00:00+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((2.38717 48.8944, 2.38717 48.8964, 2.38917 48.8964, 2.38917 48.8944, 2.38717 48.8944))','ANQES2017','ANQES 2017','ANQES','<p>À l''occasion des 8es Assises nationales de la qualité de l''environnement sonore, le CIDB vous propose de participer à une NoiseCapture Party. Pendant toute la durée de l’événement du 27 au 29 novembre, utilisez le code ANQES, mesurez les niveaux sonores durant vos trajets quotidiens et contribuez à l''élaboration de cartes de bruit. RDV au stand NoiseCapture pour plus d''informations.</p>','2017-10-25 03:00:00+02','2017-11-29 02:00:00+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((7.75459 48.5798, 7.75459 48.5891, 7.76949 48.5891, 7.76949 48.5798, 7.75459 48.5798))','FDSSTRAS2018','Stras'' NoiseCaptureParty ','FDSSTRAS','À l''occasion de la Fête de la Science du 12 au 14 octobre 2018, l''UMRAE vous propose de participer à une NoiseCapture Party. Mesurez l''environnement sonore autour du Village des Sciences de Strasbourg avec votre smartphone et contribuez à l''élaboration d''une carte de bruit en temps réel avec le code #FDSSTRAS. Rendez-vous au stand "Du son au bruit" pour plus d''informations. ','2018-10-11 03:00:00+02','2018-10-16 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('SRID=4326;POLYGON ((-4.175032630461101 47.83646295483396, -4.175032630461101 47.861274884397034, -4.149282696718034 47.861274884397034, -4.149282696718034 47.83646295483396, -4.175032630461101 47.83646295483396))','IMS2018','Immersion Sciences 2018','IMS2018','"NoiseCapture": Capturez le bruit ! Aidez les scientifiques à cartographier l''environnement sonore.',NULL,NULL,false,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((7.695 48.567, 7.695 48.582, 7.715 48.582, 7.715 48.567, 7.695 48.567))','MSA2019','Maison pour la Science en Alsace','MSA','Dans le cadre du parcours de formation "A mon signal : son à volonté !", l''UMRAE propose de participer à une NoiseCapture Party. Les participants sont invités à mesurer l''environnement sonore autour du Laboratoire de Strasbourg du Cerema avec leur smartphone et à contribuer à l''élaboration d''une carte de bruit en temps réel avec le code #MSA.','2019-01-09 02:00:00+01','2019-01-12 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-1.74 47.12, -1.74 47.33, -1.43 47.33, -1.43 47.12, -1.74 47.12))','FDSNTS2018','NoiseCapture s''invite à la fête - Nantes','FDSNTS','Profitez de la Fête de la Science pour découvrir NoiseCapture et pour participer à l''élaboration participative de la carte de bruit de Nantes Métropole. Rendez-vous sur notre stand à l''Hôtel de la Région, le samedi 13 octobre de 10h à 18h et le dimanche 14 octobre de 14h à 18h.','2018-10-11 03:00:00+02','2018-10-16 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((9.447 42.696, 9.447 42.706, 9.456 42.706, 9.456 42.696, 9.447 42.696))','AGGLOBASTIA2018','Agglo Bastia Noise Party','AGGLOBASTIA','Avec la Communauté d''Agglomération de Bastia, le CNRS et l''Ifsttar, participez à un grand projet de recherche sur le bruit qui vous entoure. Pour ce faire, téléchargez l’application gratuite NoiseCapture sur votre smartphone Android et captez tous les bruits où que vous soyez sur l''emprise spatiale. Ils seront répertoriés dans une base de données qui permettra d’établir une cartographie du bruit à l''échelle mondiale. Et vous contribuerez aussi à la première étape d''un plan d''action de la CAB visant à mieux connaître et réduire les nuisances sonores.','2018-10-04 03:00:00+02','2019-01-01 00:59:59+01',true,false);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((14.772628777411983 40.76160691211383, 14.772628777411983 40.78173647669187, 14.81013159066223 40.78173647669187, 14.81013159066223 40.76160691211383, 14.772628777411983 40.76160691211383))','UNISA','University of Salerno 2018','UNISA','NoiseCapture Party made by the University of Salerno','2018-05-03 03:00:00+02','2018-07-30 03:00:00+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-8.431 43.319, -8.431 43.38, -8.372 43.38, -8.372 43.319, -8.431 43.319))','UDC_2019','Contaminación acústica da Universidade da Coruña','UDC','Descuberta de contaminación acústica: En grupos faremos un mapeado acústico da UDC e da cidade da Coruña, localizando os lugares con contaminación acústica. Traballaremos en grupos e resolveremos ao final da xornada.','2019-02-24 02:00:00+01','2019-04-25 01:59:59+02',true,false);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-1.608 47.197, -1.608 47.27, -1.506 47.27, -1.506 47.197, -1.608 47.197))','GEO2019','Semaine "hors salle" L1 géographie et aménagement','GEO2019','L''université de Nantes organise chaque année, pour ses étudiants en L1 géographie et aménagement, une semaine « hors salle». Pendant cette semaine, les étudiants travaillent autour d''un projet défini par l''équipe pédagogique. Cette année, Claire Guiu et Christèle Allès (membres de cette équipe) s''associent à l''UMRAE pour aborder la thématique des environnements/paysages sonores.','2019-03-11 02:00:00+01','2019-03-17 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((4.806349 45.742476, 4.806349 45.789497, 4.864899 45.789497, 4.864899 45.742476, 4.806349 45.742476))','H2020Monica_Lyon2018','H2020 Monica Fête Des Lumières Party','H2020','Fête Des Lumières is an event that takes place in Lyon every nights between the 6th and 9th of December. For four nights, the keys of the city are given to different artists who light up buildings, street, squares and parks. All the city center becomes pedestrian and the sound environment changes with new sources like music or crowd noise.','2018-12-06 02:00:00+01','2018-12-10 02:00:00+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-4.175 47.836, -4.175 47.861, -4.149 47.861, -4.149 47.836, -4.175 47.836))','IMS2019','Immersion Sciences 2019','IMS2019','"NoiseCapture": Capturez le bruit ! Aidez les scientifiques à cartographier l''environnement sonore.','2019-03-26 02:00:00+01','2019-03-31 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((4.779 45.769, 4.779 45.793, 4.808 45.793, 4.808 45.769, 4.779 45.769))','FPSLYO2019','NoiseCapture au festival Pop’Sciences : « La Duchère » Party','FPSLYO','Une NoiseCapture Party est proposée au quartier de la Duchère à Lyon dans le cadre du festival Pop’Sciences organisé par l’Université de Lyon les 17 et 18 mai 2019. Scolaires et grand public pourront découvrir l’application et contribuer à la cartographie participative de l’environnement sonore du quartier.','2019-05-01 03:00:00+02','2019-06-01 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((0.4055 40.6706, 0.4055 42.0826, 2.9396 42.0826, 2.9396 40.6706, 0.4055 40.6706))','SSSOROLL2019','Activitat Cerquem el silenci a ...','SSSOROLL2019','Dins de la Setmana Sense Soroll 2019, es promou l’activitat de localitzar, identificar i caracteritzar àrees, zones i itineraris tranquils/les acústicament, a nivell urbà, interurbà o que no formin part de l’entramat urbà, com camins rurals, agrícoles, etc..., per tal de disposar d’informació dels diferents espais lliures de soroll. Aquesta activitat es realitzarà amb mòbil i l’app NOISECAPTURE','2019-04-16 03:00:00+02','2019-05-31 01:59:59+02',true,false);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((14.772628777411983 40.76160691211383, 14.772628777411983 40.78173647669187, 14.81013159066223 40.78173647669187, 14.81013159066223 40.76160691211383, 14.772628777411983 40.76160691211383))','UNISA2019','University of Salerno 2019','UNISA','NoiseCapture Party made by the University of Salerno','2019-05-22 01:59:59+02','2019-05-26 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((7.75459 48.5798, 7.75459 48.5891, 7.76949 48.5891, 7.76949 48.5798, 7.75459 48.5798))','FDSSTRAS2019','Stras'' NoiseCaptureParty ','FDSSTRAS','À l''occasion de la Fête de la Science 2019, l''UMRAE vous propose de participer à une NoiseCapture Party. Mesurez l''environnement sonore autour du Village des Sciences de Strasbourg avec votre smartphone et contribuez à l''élaboration d''une carte de bruit en temps réel avec le code #FDSSTRAS. Rendez-vous à 15h les 12 et 13 octobre devant les marches du Palais Universitaire ou du 11 au 13 octobre au stand "Des bruits et des sons" pour plus d''informations. ','2019-10-10 03:00:00+02','2019-10-15 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((4.806349 45.742476, 4.806349 45.789497, 4.864899 45.789497, 4.864899 45.742476, 4.806349 45.742476))','H2020Monica_Lyon2019','H2020 Monica Fête Des Lumières Party','H2020','Fête Des Lumières is an event that takes place in Lyon every nights between the 6th and 9th of December. For four nights, the keys of the city are given to different artists who light up buildings, streets, squares and parks. All the city center becomes pedestrian and the sound environment changes with new sources like music or crowd noise.','2019-12-04 02:00:00+01','2019-12-10 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-8.431 43.319, -8.431 43.38, -8.372 43.38, -8.372 43.319, -8.431 43.319))','UDC_2020','Contaminación acústica da Universidade da Coruña','UDC','Descuberta de contaminación acústica: En grupos faremos un mapeado acústico da UDC e da cidade da Coruña, localizando os lugares con contaminación acústica. Traballaremos en grupos e resolveremos ao final da xornada.','2020-03-01 02:00:00+01','2020-03-21 00:59:59+01',true,false);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((7.69844 48.57223, 7.69844 48.57837, 7.71454 48.57837, 7.71454 48.57223, 7.69844 48.57223))','MSA2020','Stras'' NoiseCaptureParty ','MSA','Dans le cadre du parcours de formation "Son - Signal - Information",  l''UMRAE propose de participer à une NoiseCapture Party. Les participants sont invités à mesurer l''environnement sonore autour du Laboratoire de Strasbourg du Cerema avec leur smartphone et à contribuer à l''élaboration d''une carte de bruit en temps réel avec le code #MSA. ','2020-01-22 01:00:00+01','2020-01-25 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-78.49644 -0.21466, -78.49644 -0.20497, -78.48312 -0.20497, -78.48312 -0.21466, -78.49644 -0.21466))','CICAM_EPN2020','NoiseCapture Campus Quito','CICAM','The first edition of the Noise Workshop is aim to sensiblize the <a href="http://www.quitoinforma.gob.ec/2019/10/02/municipio-y-universidades-dan-vida-al-proyecto-urbanistico-campus-quito/" target="_blank">university citadel</a> about the noise pollution and promote the colaboration between universities, students, goverment and general public to tackle the noise problem and promote actions against it.','2020-04-21 03:00:00+02','2020-04-27 01:59:59+02',true,false);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-9.382324 41.767215, -9.382324 43.838489, -6.61377 43.838489, -6.61377 41.767215, -9.382324 41.767215))','UDC_COVID_2020','Universidade da Coruña - COVID lockdown','UDC_COVID','Noise measurement campaign, made by UDC, during the COVID lockdown.','2020-05-04 03:00:00+02','2020-05-21 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((14.4034 45.9722, 14.4034 46.1558, 14.7675 46.1558, 14.7675 45.9722, 14.4034 45.9722))','LJUBLJANA_2020','CitieS-Health Ljubljana','CHLJ','CitieS-Health is a H2020 project (https://citieshealth.eu/) that aims to put citizens’ concerns at the heart of research agenda on environmental epidemiology by tackling health issues that concern them. In Ljubljana, we will investigate how the quality of the living environment (with an emphasis on noise) and living habits affect the (mental) health and well-being of individuals.','2020-10-01 03:00:00+02','2021-07-02 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-8.431 43.319, -8.431 43.38, -8.372 43.38, -8.372 43.319, -8.431 43.319))','UDC_2021','Contaminación acústica da Universidade da Coruña','UDC','Descuberta de contaminación acústica: En grupos faremos un mapeado acústico da UDC e da cidade da Coruña, localizando os lugares con contaminación acústica. Traballaremos en grupos e resolveremos ao final da xornada.','2021-03-01 02:00:00+01','2021-03-21 00:59:59+01',true,false);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-0.2082 51.4942, -0.2082 51.5048, -0.1915 51.5048, -0.1915 51.4942, -0.2082 51.4942))','NCHData','Noise as a Common Hazard','NCH','This workshop will introduce commons-based approaches to tackling noise pollution. By using smartphones and open source applications to conduct noise surveys in their neighbourhoods, participants will create audio streams that can be shared online. Participants will learn how these shared repositories of data and media can be used as evidence towards civic rights, and ways in which community soundscapes can be managed.','2021-07-02 03:00:00+02','2021-07-05 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('SRID=4326;POLYGON ((-141.00686645507812 41.6769256591798, -141.00686645507812 83.11042022705084, -52.61888885498041 83.11042022705084, -52.61888885498041 41.6769256591798, -141.00686645507812 41.6769256591798))','NoiseCANADA21','Acoustics Week in Canada 2021','NC21','The organizing committee of Acoustics Week in Canada 2021 invites you to contribute to the setup of the largest noise map in Canada in terms of geographical range. This challenge will bring the Canada’s acoustics community together and will provide a concrete output to illustrate its drive and presence while concerns about noise are growing. During AWC21, invite colleagues, students to measure their neighboring sound environment with a smartphone and to contribute to the creation of this noise map in real time with the code #NC21. Visit the "Noise map Canada" booth on the AWC21 conference platform for more information.','2021-07-23 03:00:00+02','2021-10-16 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-1.67 43.3643, -1.67 43.4237, -1.596 43.4237, -1.596 43.3643, -1.67 43.3643))','SJDL2021','Capturer le bruit pour mieux le combattre à Saint-Jean-De-Luz','SJDL','Une expérience pédagogique et collaborative pour capturer le bruit, mieux l’identifier et produire des propositions donner un Nouvel Elan à Saint-Jean-De-Luz ! ','2021-08-01 03:00:00+02','2022-01-01 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((4.285041809 49.782264709, 4.285041809 50.649921417, 5.409042835 50.649921417, 5.409042835 49.782264709, 4.285041809 49.782264709))','WAL_NAM_PEDA_2022','Wallonie - Namur - Party pédagogique 2022','NAMPE','Rassemblement de classes du secondaires pour sonder l''environnement sonore à l''échelle d''un quartier. Une occasion se sensibiliser à la problèmatique du bruit et d''amorcer le dialogue pour cerner les causes et conséquences de la pollution sonore.','2021-12-09 02:00:00+01','2022-05-31 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((4.285041809 49.782264709, 4.285041809 50.649921417, 5.409042835 50.649921417, 5.409042835 49.782264709, 4.285041809 49.782264709))','WAL_NAM_CIT_2022','Wallonie - Namur - Party citoyenne 2022','NAMCI','Rassemblement de citoyens pour sonder l''environnement sonore à l''échelle d''un quartier. Une occasion se sensibiliser à la problèmatique du bruit et d''amorcer le dialogue pour cerner les causes et conséquences de la pollution sonore.','2021-12-09 02:00:00+01','2022-05-31 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-5.14375114440918 41.33375167846674, -5.14375114440918 51.08939743041998, 9.560416221618766 51.08939743041998, 9.560416221618766 41.33375167846674, -5.14375114440918 41.33375167846674))','JNA2022','NoiseCapture Party JNA 2022','JNA2022','Quelles sont les réalités du bruit dans les villes, les villages, les quartiers ? Grâce à la NoiseCapture Party JNA2022, rendez-visible le bruit de votre quotidien !','2022-02-27 02:00:00+01','2022-07-01 01:59:59+02',true,false);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-4.175 47.836, -4.175 47.861, -4.149 47.861, -4.149 47.836, -4.175 47.836))','IMS2022','Immersion Sciences 2022','IMS2022','"NoiseCapture": Capturez le bruit ! Aidez les scientifiques à cartographier l''environnement sonore.','2022-03-27 03:00:00+02','2022-04-03 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((14.772628777411983 40.76160691211383, 14.772628777411983 40.78173647669187, 14.81013159066223 40.78173647669187, 14.81013159066223 40.76160691211383, 14.772628777411983 40.76160691211383))','UNISA2022','University of Salerno 2022','UNISA','NoiseCapture Party made by the University of Salerno','2022-05-15 03:00:00+02','2022-05-19 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((2.3346179 48.8568894, 2.3346179 48.8723885, 2.3584574 48.8723885, 2.3584574 48.8568894, 2.3346179 48.8568894))','HBM2022','Les paysages sonores de Paris Centre','HBM','NoiseCapture Party sur Paris Centre (arrondissements 1, 2, 3, 4), des grands boulevards à la rue de Rivoli, des Halles au Centre Pompidou, animée par les habitants du quartier Halles Beaubourg Montorgueil','2022-07-10 03:00:00+02','2022-12-31 22:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-71.935392 45.37132, -71.935392 45.3849, -71.91834 45.3849, -71.91834 45.37132, -71.935392 45.37132))','UdeSBruit2022','NoiseCapture à UdeS','UDES','Les étudiant.e.s des cours GMC140 et GMC720 réalisent une carte de bruit du campus principal dans le cadre de leur formation, et vont impliquer la communauté universitaire','2022-11-13 02:00:00+01','2022-12-01 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-70.71748 -33.50073, -70.71748 -33.39461, -70.59211 -33.39461, -70.59211 -33.50073, -70.71748 -33.50073))','SantiagoNoise2022','NoiseCapture at Santiago','SANTI','Pr Robin and Pr Meruane will organize a NoiseCapture Party during a research / teaching stay of Pr Robin at Universitat de Chile, Santiago (stay funded by Canada and Québec)','2022-10-25 03:00:00+02','2022-11-16 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((14.772628777411983 40.76160691211383, 14.772628777411983 40.78173647669187, 14.81013159066223 40.78173647669187, 14.81013159066223 40.76160691211383, 14.772628777411983 40.76160691211383))','UNISA2022_11','University of Salerno 2022','UNISA','NoiseCapture Party made by the University of Salerno','2022-11-08 02:00:00+01','2022-11-14 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-8.6658 40.6183, -8.6658 40.6432, -8.6348 40.6432, -8.6348 40.6183, -8.6658 40.6183))','UnivAveiro2022','NoiseCapture Party 2022 @UA','UAPARTY','The NoiseCapture Party at the University of Aveiro will be held on the 15th of December, 2022, within the course “Energy, Mobility, and Transportation” (taught by Prof. Margarida Coelho at Mechanical Engineering Department). Students will be trained before the event with notions about fundamental noise parameters (i.e., sound pressure level, equivalent continuous sound pressure level, sound power level, percentile levels) and on the NoiseCapture app. They will be divided into groups in order to monitor different parts of the University of Aveiro campus, where different noise sources are supposed to be present.','2022-12-14 02:00:00+01','2022-12-17 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-73.57 45.488, -73.57 45.499, -73.556 45.499, -73.556 45.488, -73.57 45.488))','ETSH2023_HeureCalme','Cartographie du bruit au Campus ÉTS pendant la période calme','ETSCALME','Les étudiants du cours d’acoustique industrielle de l’ÉTS Montréal devront cartographier le bruit dans le campus de l’ÉTS pendant un temps « calme » (c’est-à-dire, hors heures de pointe).','2023-02-21 02:00:00+01','2023-02-24 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-73.57 45.488, -73.57 45.499, -73.556 45.499, -73.556 45.488, -73.57 45.488))','ETSH2023_HeurePointe','Cartographie du bruit au Campus ÉTS pendant la période d’heure de pointe','ETSPOINTE','Les étudiants du cours d’acoustique industrielle de l’ÉTS Montréal devront cartographier le bruit dans le campus de l’ÉTS pendant l’heure de pointe.','2023-02-21 02:00:00+01','2023-02-24 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-2.7826 47.6394, -2.7826 47.6485, -2.7666 47.6485, -2.7666 47.6394, -2.7826 47.6394))','IUTVannes2023','BUT Science des données - IUT Vannes','IUT','NoiseCapture party organisée dans le cadre du cours SIG au sein du BUT Science des données - IUT de Vannes','2023-03-19 02:00:00+01','2023-03-26 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-1.6514 47.1499, -1.6514 47.1604, -1.6332 47.1604, -1.6332 47.1499, -1.6514 47.1499))','FUTUREDays2023','FUTURE Days - Nantes 2023','FD23','NoiseCapture party organisée dans le cadre des FUTURE Days 2023, sur le campus de Nantes (https://www.futuredays.fr/)','2023-04-11 03:00:00+02','2023-04-14 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-71.95 45.36, -71.95 45.39, -71.9 45.39, -71.9 45.36, -71.95 45.36))','GMC2023','Cartographie de bruit sur le campus principal de l’université de Sherbrooke','GMC','Dans le cadre du cours ‘Acoustique et contrôle du bruit’, ce projet concret de formation vise à réaliser une carte de bruit sur le campus principal de l’université de Sherbrooke. La carte mesurée pourra être comparée à des simulations réalisées avec des outils comme le logiciel NoiseModelling.','2023-10-01 03:00:00+02','2023-12-16 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-73.64 45.48, -73.64 45.54, -73.53 45.54, -73.53 45.48, -73.64 45.48))','AWC23','Acoustics Week in Canada 2023 - Noise Mapping activity','AWC23','What about mapping the sound environment in Montréal during AWC23 conference? With NoiseCapture, you can easily measure and share your sound environment data. Our collective challenge during the conference is to surpass 1000 measurements, don''t miss this opportunity to contribute to the sound mapping initiative and be part of an impactful project.','2023-10-01 03:00:00+02','2023-10-16 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-46.7503 -23.5748, -46.7503 -23.55128, -46.7095 -23.55128, -46.7095 -23.5748, -46.7503 -23.5748))','SAOPAULO23','University of São Paulo 2023','USP23','Acoustic map made by students from the Architecture and Urbanism course at the University of São Paulo. The students'' smartphones were pre-calibrated with pink noise in a controlled environment. The camping encompasses four main points: (1) the faculty hall; (2) near the avenue; (3) a path with the sounds of birds; and (4) the Atlantic forest.','2023-12-03 02:00:00+01','2023-12-09 02:00:00+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-73.565 45.486, -73.565 45.498, -73.55 45.498, -73.55 45.486, -73.565 45.486))','ETSH2024_heurecalme','Cartographie du bruit au Campus ÉTS pendant la période calme - 2024','H24CREUSE','Les étudiants du cours d’acoustique industrielle de l’ÉTS Montréal devront cartographier le bruit dans le campus de l’ÉTS pendant un temps « calme » (c’est-à-dire, hors heures de pointe).','2024-02-20 02:00:00+01','2024-02-23 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((9.1569 48.7671, 9.1569 48.7742, 9.1706 48.7742, 9.1706 48.7671, 9.1569 48.7671))','SUPERBLOCKINFO','SUPERBLOCK Infotag','URB','Möchten Sie wissen, wie hoch der Lärmpegel ist, dem Sie alltäglich ausgesetzt sind?Hiermit können sie diesen selber messen!Dies ist ein Test, um die Verwendung der NoiseCapture-App im SUPERBLOCK zu demonstrieren.','2023-09-12 03:00:00+02','2023-09-30 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((14.772628777411983 40.76160691211383, 14.772628777411983 40.78173647669187, 14.81013159066223 40.78173647669187, 14.81013159066223 40.76160691211383, 14.772628777411983 40.76160691211383))','UNISA2023_10','University of Salerno 2023','UNISA','NoiseCapture Party made by the students from University of Salerno, leaded by Prof. Claudio Guarnaccia','2023-10-25 03:00:00+02','2023-10-28 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-1.562212499902201 47.205702936279664, -1.562212499902201 47.20888426648492, -1.554090831418477 47.20888426648492, -1.554090831418477 47.205702936279664, -1.562212499902201 47.205702936279664))','VAU_ENSA_23','ENSA Sound Environmental Study','ENSA','Master''s students from the Nantes School of Architecture collaborate to create a participative Noise Map. Through a single campaign of measurements, the sound environment of the neighborhood is discussed and evaluated.','2023-11-07 02:00:00+01','2023-11-11 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-73.565 45.486, -73.565 45.498, -73.55 45.498, -73.55 45.486, -73.565 45.486))','ETSH2024_HeurePointe','Cartographie du bruit au Campus ÉTS pendant la période de pointe - 2024','H24POINTE','Les étudiants du cours d’acoustique industrielle de l’ÉTS Montréal devront cartographier le bruit dans le campus de l’ÉTS pendant  l’heure de pointe.','2024-02-20 02:00:00+01','2024-02-23 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-8.6658 40.6183, -8.6658 40.6432, -8.6348 40.6432, -8.6348 40.6183, -8.6658 40.6183))','UnivAveiro2023','NoiseCapture Party 2023 @UA','UAPARTY','The NoiseCapture Party at the University of Aveiro will be held on the 23rd of November, 2023, within the course “Energy, Mobility, and Transportation” (taught by Prof. Margarida Coelho in the Mechanical Engineering Department). Students will be trained before the event with notions about fundamental noise parameters (i.e., sound pressure level, equivalent continuous sound pressure level, sound power level, percentile levels) and on the NoiseCapture app. They will be divided into groups in order to monitor different parts of the University of Aveiro campus, where different noise sources are supposed to be present.','2023-11-22 02:00:00+01','2023-11-25 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((0.19 48, 0.19 48.01, 0.21 48.01, 0.21 48, 0.19 48))','LMS2024','Le Mans Sonore - 2024','LMS','Dans le cadre de la "Biennale du son, Le Mans Sonore" l''équipe NoiseCapture vous propose de venir, le samedi 20 janvier, participer à une NoiseCapture Party pour cartographier les environnements sonores du centre ville.','2024-01-19 02:00:00+01','2024-01-23 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-71.97 45.36, -71.97 45.44, -71.82 45.44, -71.82 45.36, -71.97 45.36))','SETO','Silence et Obscurité','SETO','L’objectif de ce projet est de réaliser une carte de bruit dans la ville de Sherbrooke, et spécialement autour du parc du Mont-Bellevue. Le but est en partie de sensibiliser la pollution sur le bruit environnemental, mais surtout de définir les contours d’une future ‘réserve de silence et d’obscurité’ dans le parc','2023-09-01 03:00:00+02','2024-09-02 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((3.06 50.62, 3.06 50.63, 3.08 50.63, 3.08 50.62, 3.06 50.62))','LILLE2024','NoiseCapture Party Lille','LILLE','NoiseCapture party réalisée dans le cadre de la Journée « Mobilisons-nous pour un environnement sonore de Qualité: aménager, gérer et rénover dans nos territoires », organisée par le Cerema et le CidB. Les participants sont invités à mesurer l''environnement sonore avec leur smartphone et à contribuer à l''élaboration d''une carte de bruit en temps réel avec le code #LILLE.','2024-04-15 02:00:00+02','2024-04-18 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((14.280537630455571 45.955402607031644, 14.280537630455571 46.11278098726268, 14.635612639738952 46.11278098726268, 14.635612639738952 45.955402607031644, 14.280537630455571 45.955402607031644))','UKLL2024','Urban Cycling Living Lab Ljubljana','UKLL','Led by the Environmental Sciences Department at Jožef Stefan Institute (JSI) in Slovenia, as part of the URBANOME H2020 project (www.urbanome.eu), we study how environmental pollution can affect cyclists and how "greener” cycle paths can help reduce the exposure.','2024-04-01 03:00:00+02','2025-03-02 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-73.599 45.534, -73.599 45.544, -73.583 45.544, -73.583 45.534, -73.599 45.534))','ETSA2024_SaintEtienne','Cartographie bruit École Saint-Étienne','ECOLE1','Les étudiants du cours d’acoustique industrielle de l’ÉTS Montréal devront cartographier le bruit l’école primaire Saint-Étienne.','2024-10-23 03:00:00+02','2024-10-26 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-73.653 45.562, -73.653 45.577, -73.637 45.577, -73.637 45.562, -73.653 45.562))','ETSA2024_SaintsMartyrsCanadiens','Cartographie bruit Saints-Martyrs-Canadiens','ECOLE2','Les étudiants du cours d’acoustique industrielle de l’ÉTS Montréal devront cartographier le bruit l’école primaire Saints-Martyrs-Canadiens.','2024-10-28 02:00:00+01','2024-10-31 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-73.571 45.566, -73.571 45.582, -73.557 45.582, -73.557 45.566, -73.571 45.566))','ETSA2024_NotreDameduFoyer','Cartographie bruit Notre-Dame-du-Foyer','ECOLE3','Les étudiants du cours d’acoustique industrielle de l’ÉTS Montréal devront cartographier le bruit l’école primaire Notre-Dame-du-Foyer.','2024-11-06 02:00:00+01','2024-11-09 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((1.4465 48.1205, 1.4465 49.2413, 3.5587 49.2413, 3.5587 48.1205, 1.4465 48.1205))','BROUHAHA2025_IdF',' BROUHAHA - IdF','BROU','Dans le cadre du projet BROUHAHA, 120 volontaires se serviront de l''application NoiseCapture pour mesurer leur environnement sonore à chaque changement d''environnement, pendant 7 jours. Ce projet vise à évaluer l''association entre l''exposition au bruit et la variation de certains marqueurs cardio-métaboliques.','2024-10-01 03:00:00+02','2025-07-01 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((2.0629 44.1154, 2.0629 46.804, 7.1859 46.804, 7.1859 44.1154, 2.0629 44.1154))','BROUHAHA2025_AuRA',' BROUHAHA - AuRA','HAHA','Dans le cadre du projet BROUHAHA, 120 volontaires se serviront de l''application NoiseCapture pour mesurer leur environnement sonore à chaque changement d''environnement, pendant 7 jours. Ce projet vise à évaluer l''association entre l''exposition au bruit et la variation de certains marqueurs cardio-métaboliques.','2024-10-01 03:00:00+02','2025-07-01 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-99.143361 19.43086, -99.143361 19.439379, -99.132814 19.439379, -99.132814 19.43086, -99.143361 19.43086))','PSJUAM24','Coloquio Internacional Paisaje Sonoro CDMX','CDMX','Este NoiseCapture Party se organiza el 17 de octubre 2024 dentro del coloquio internacional sobre Paisaje Sonoro: "Espacio público y ciudadanía en centros urbanos y centros históricos de la Ciudad de México.El objetivo del coloquio es crear un encuentro académico internacional con líderes de investigación para promover la difusión y discusión de temas relacionados al sonido, el espacio público y la ciudadanía en contextos urbanos y centros históricos. Está dirigido a Investigadores, académicos, artistas, profesionales y estudiantes interesados en la divulgación de trabajos, que busquen crear un diálogo dentro de las témáticas del sonido, del espacio y de la ciudadanía.En esta ocasión, la NoiseCapture Party alimentará un mapa sonoro colaborativo del centro histórico de la Ciudad de México y abrirá un debate sobre el Paisaje Sonoro.','2024-10-16 03:00:00+02','2024-10-19 01:59:59+02',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((14.772628777411983 40.76160691211383, 14.772628777411983 40.78173647669187, 14.81013159066223 40.78173647669187, 14.81013159066223 40.76160691211383, 14.772628777411983 40.76160691211383))','UNISA2024_11','University of Salerno 2024','UNISA','NoiseCapture Party made by the students from University of Salerno, leaded by Prof. Claudio Guarnaccia','2024-11-04 02:00:00+01','2024-11-09 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-73.54607 45.54124, -73.54607 45.54716, -73.53792 45.54716, -73.53792 45.54124, -73.54607 45.54124))','ETSH2025_Baril','Cartographie bruit École Baril','ECOLE3','Les étudiant.e.s du cours d’acoustique industrielle de l’ÉTS Montréal devront cartographier le bruit autour de l’école primaire Baril.','2025-03-17 02:00:00+01','2025-03-20 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-73.65427 45.45524, -73.65427 45.46821, -73.63728 45.46821, -73.63728 45.45524, -73.65427 45.45524))','ETSH2025_SaintCatherineDeSienne','Cartographie bruit École Sainte-Catherine-de-Sienne','ECOLE1','Les étudiant.e.s du cours d’acoustique industrielle de l’ÉTS Montréal devront cartographier le bruit autour de l’école primaire Sainte-Catherine-de-Sienne.','2025-02-24 02:00:00+01','2025-02-27 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-73.5983 45.55496, -73.5983 45.56657, -73.57959 45.56657, -73.57959 45.55496, -73.5983 45.55496))','ETSH2025_SainteBernadetteSoubirous','Cartographie bruit École Sainte-Bernadette-Soubirous','ECOLE2','Les étudiant.e.s du cours d’acoustique industrielle de l’ÉTS Montréal devront cartographier le bruit autour de l’école primaire Sainte-Bernadette-Soubirous.','2025-03-10 02:00:00+01','2025-03-13 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((2.3647 48.80037, 2.3647 48.82538, 2.39895 48.82538, 2.39895 48.80037, 2.3647 48.80037))','Campus_Urbain_2025','AutiSenCité Ivry-sur-Seine','IVRY','Il s’agit de mesurer le niveau sonore de l’environnement urbain et recueillir le ressenti des habitants – notamment l’impact sur leur qualité de vie – sur un cheminement qu’ils utilisent habituellement. Cette étude s’inscrit dans un cadre plus large qui a comme objectif scientifique d’améliorer la prise en compte de la sensorialité dans la ville, notamment pour les personnes autistes qui ont une sensorialité atypique.','2025-01-29 02:00:00+01','2025-11-03 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((2.42822 48.77526, 2.42822 48.80074, 2.46828 48.80074, 2.46828 48.77526, 2.42822 48.77526))','Autisencite_upec','Marche sensorielle UPEC','UPEC','Il s’agit de mesurer le niveau sonore de l’environnement urbain autour du campus de l’Université Pars Est Créteil et de recueillir le ressenti des étudiants sur un cheminement qu’ils utilisent habituellement. Cette étude s’inscrit dans un cadre plus large qui a comme objectif scientifique d’améliorer la prise en compte de la sensorialité dans la ville, notamment pour les personnes autistes qui ont une sensorialité atypique.','2025-02-28 02:00:00+01','2025-12-02 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((2.31371 48.79356, 2.31371 48.80753, 2.33392 48.80753, 2.33392 48.79356, 2.31371 48.79356))','Autisencite_ sessadgrangeory','Marche sensorielle SESSAD Grange-Ory','SESSADGO','Il s’agit de mesurer le niveau sonore de l’environnement urbain autour du SESSAD Grange Ory à Cachan dans le cadre d’un projet de recherche qui vise à comprendre le rapport de personnes autistes avec les éléments sensoriels de la ville.','2025-02-20 02:00:00+01','2025-12-02 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((2.36561 48.80691, 2.36561 48.82546, 2.39297 48.82546, 2.39297 48.80691, 2.36561 48.80691))','Autisencite_imearpege','Marche sensorielle IME Arpège','ARPEGE','Il s’agit de mesurer le niveau sonore de l’environnement urbain autour de l’IME Arpège à Ivry-sur-Seine dans le cadre d’un projet de recherche qui vise à comprendre le rapport de personnes autistes avec les éléments sensoriels de la ville.','2025-02-28 02:00:00+01','2025-12-02 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((-1.63502 47.32189, -1.63502 47.34182, -1.60073 47.34182, -1.60073 47.32189, -1.63502 47.32189))','Autisencite_FAM','Marche sensorielle FAM Diapason','FAM','Il s’agit de mesurer le niveau sonore de l’environnement urbain autour du FAM Diapason à Grandchamps-des-Fontaines dans le cadre d’un projet de recherche qui vise à comprendre le rapport de personnes autistes avec les éléments sensoriels de la ville.','2025-02-28 02:00:00+01','2025-12-02 00:59:59+01',true,true);
--INSERT INTO public.noisecapture_party (the_geom,layer_name,title,tag,description,start_time,end_time,filter_time,filter_area) VALUES ('POLYGON ((2.35795 48.85079, 2.35795 48.86262, 2.37538 48.86262, 2.37538 48.85079, 2.35795 48.85079))','Autisencite_CDVenise','Marche sensorielle IME Cours de Venise','CDV','Il s’agit de mesurer le niveau sonore de l’environnement urbain autour de l’IME Cours de Venise à Paris dans le cadre d’un projet de recherche qui vise à comprendre le rapport de personnes autistes avec les éléments sensoriels de la ville.','2025-02-28 02:00:00+01','2025-12-02 00:59:59+01',true,true);


-- Table: NOISECAPTURE_TRACK
CREATE TABLE NOISECAPTURE_TRACK (
    PK_TRACK serial  NOT NULL,
    PK_USER int  NOT NULL REFERENCES noisecapture_user (PK_USER) ON UPDATE CASCADE ON DELETE CASCADE,
    TRACK_UUID char(36)  NOT NULL,
	VERSION_NUMBER int NOT NULL,
	RECORD_UTC timestamptz NOT NULL,
	PLEASANTNESS float,
	DEVICE_PRODUCT text  NOT NULL,
	DEVICE_MODEL text NOT NULL,
	DEVICE_MANUFACTURER text NOT NULL,
	NOISE_LEVEL float NOT NULL,
	TIME_LENGTH float NOT NULL,
	GAIN_CALIBRATION float NOT NULL DEFAULT 0,
	CALIBRATION_METHOD text NOT NULL DEFAULT 'None',
	MICROPHONE_IDENTIFIER text NOT NULL DEFAULT '',
	MICROPHONE_SETTINGS text NOT NULL DEFAULT '',
	PK_PARTY int REFERENCES noisecapture_party (PK_PARTY) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT NOISECAPTURE_TRACK_PK PRIMARY KEY (PK_TRACK)
);

COMMENT ON COLUMN NOISECAPTURE_TRACK.NOISE_LEVEL IS 'Sound level in dB(A)';
COMMENT ON COLUMN NOISECAPTURE_TRACK.VERSION_NUMBER IS 'Application version identifier';
COMMENT ON COLUMN NOISECAPTURE_TRACK.PLEASANTNESS IS 'PLEASANTNESS ratio, from 0 to 100';
COMMENT ON COLUMN NOISECAPTURE_TRACK.GAIN_CALIBRATION IS 'Signal gain in dB, provided from user using phone calibration';
COMMENT ON COLUMN NOISECAPTURE_TRACK.TIME_LENGTH IS 'Length of measurement in seconds';

-- Table: NOISECAPTURE_POINT
CREATE TABLE NOISECAPTURE_POINT (
    PK_POINT serial NOT NULL,
    THE_GEOM geometry, -- POSTGIS ONLY the_geom geometry(GeometryZ, 4326),
    PK_TRACK int NOT NULL REFERENCES noisecapture_track (pk_track) ON UPDATE CASCADE ON DELETE CASCADE,
    NOISE_LEVEL float  NOT NULL,
    SPEED float,
    ACCURACY float  NOT NULL,
    ORIENTATION float,
    TIME_DATE timestamptz  NOT NULL,
    TIME_LOCATION timestamptz,
    CONSTRAINT NOISECAPTURE_POINT_PK PRIMARY KEY (PK_POINT)
);

COMMENT ON COLUMN NOISECAPTURE_POINT.ORIENTATION IS 'Device movement bearing, may be null';
COMMENT ON COLUMN NOISECAPTURE_POINT.TIME_LOCATION IS 'Time of acquisition of the localisation';
COMMENT ON COLUMN NOISECAPTURE_POINT.TIME_DATE IS 'Time of the noise level measurement';
COMMENT ON COLUMN NOISECAPTURE_POINT.SPEED IS 'Device speed in m/s. May be null';
COMMENT ON COLUMN NOISECAPTURE_POINT.ACCURACY IS 'Estimated location accuracy in meter';
COMMENT ON COLUMN NOISECAPTURE_POINT.NOISE_LEVEL IS 'Sound level in dB(A)';


-- Table: NOISECAPTURE_FREQ
CREATE TABLE NOISECAPTURE_FREQ (
    PK_POINT int  NOT NULL REFERENCES noisecapture_point (pk_point) ON DELETE CASCADE ON UPDATE CASCADE,
    FREQUENCY smallint  NOT NULL,
    NOISE_LEVEL float NOT NULL   ,
    CONSTRAINT NOISECAPTURE_FREQ_PK PRIMARY KEY (PK_POINT, FREQUENCY)
);

COMMENT ON COLUMN NOISECAPTURE_FREQ.FREQUENCY IS 'Frequency Hz';
COMMENT ON COLUMN NOISECAPTURE_FREQ.NOISE_LEVEL IS 'Sound level in dB(A)';

CREATE TABLE NOISECAPTURE_TAG (
    PK_TAG serial  NOT NULL,
    TAG_NAME text NOT NULL,
    CONSTRAINT NOISECAPTURE_TAG_PK PRIMARY KEY (PK_TAG)
);


CREATE TABLE NOISECAPTURE_TRACK_TAG (
    PK_TRACK int NOT NULL REFERENCES NOISECAPTURE_TRACK (PK_TRACK) ON DELETE CASCADE ON UPDATE CASCADE,
    PK_TAG int NOT NULL REFERENCES NOISECAPTURE_TAG (PK_TAG) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT NOISECAPTURE_TRACK_TAG_PK PRIMARY KEY (PK_TRACK, PK_TAG)
);

-- Table: NOISECAPTURE_PROCESS_QUEUE, tracks inserted but not processed for community map
CREATE TABLE NOISECAPTURE_PROCESS_QUEUE (
    PK_TRACK int PRIMARY KEY REFERENCES noisecapture_track (pk_track) ON DELETE CASCADE ON UPDATE CASCADE
);

COMMENT ON COLUMN NOISECAPTURE_PROCESS_QUEUE.PK_TRACK IS 'Update area that contains this track';

-- Table: NOISECAPTURE_AREA_CLUSTER, Variable size hexagons that contains only the measurement count
CREATE TABLE NOISECAPTURE_AREA_CLUSTER (
    CELL_LEVEL smallint NOT NULL,
    CELL_Q bigint NOT NULL,
    CELL_R bigint NOT NULL,
    THE_GEOM geometry NOT NULL,
    MEASURE_COUNT int NOT NULL,
    CONSTRAINT NOISECAPTURE_AREA_CLUSTER_PK PRIMARY KEY (CELL_LEVEL, CELL_Q, CELL_R)
);

COMMENT ON COLUMN NOISECAPTURE_AREA_CLUSTER.CELL_LEVEL IS 'Hexagonal size exponent 3^n';
COMMENT ON COLUMN NOISECAPTURE_AREA_CLUSTER.CELL_Q IS 'Hexagonal index Q';
COMMENT ON COLUMN NOISECAPTURE_AREA_CLUSTER.CELL_R IS 'Hexagonal index R';
COMMENT ON COLUMN NOISECAPTURE_AREA_CLUSTER.THE_GEOM IS 'Area shape';
COMMENT ON COLUMN NOISECAPTURE_AREA_CLUSTER.MEASURE_COUNT IS 'noisecapture_point entities in this area';

-- Table: NOISECAPTURE_AREA, Post-processed results, merge of measurements in a regular area
CREATE TABLE NOISECAPTURE_AREA (
    PK_AREA serial  NOT NULL,
    CELL_Q bigint NOT NULL,
    CELL_R bigint NOT NULL,
    TZID VARCHAR(255) NOT NULL,
    THE_GEOM geometry NOT NULL,
    LAEQ float NOT NULL,
    LA50 float NOT NULL,
    LDEN float NOT NULL,
	MEAN_PLEASANTNESS float,
    MEASURE_COUNT int NOT NULL,
    FIRST_MEASURE timestamptz NOT NULL,
    LAST_MEASURE timestamptz NOT NULL,
    PK_PARTY int REFERENCES noisecapture_party (PK_PARTY) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT NOISECAPTURE_AREA_PK PRIMARY KEY (PK_AREA)
);

COMMENT ON COLUMN NOISECAPTURE_AREA.CELL_Q IS 'Hexagonal index Q';
COMMENT ON COLUMN NOISECAPTURE_AREA.CELL_R IS 'Hexagonal index R';
COMMENT ON COLUMN NOISECAPTURE_AREA.TZID IS 'TimeZone identifier';
COMMENT ON COLUMN NOISECAPTURE_AREA.THE_GEOM IS 'Area shape';
COMMENT ON COLUMN NOISECAPTURE_AREA.MEASURE_COUNT IS 'noisecapture_point entities in this area';


-- Table: NOISECAPTURE_AREA, Post-processed results, merge of measurements in a regular area
CREATE TABLE NOISECAPTURE_AREA_PROFILE (
    PK_AREA int  NOT NULL,
    HOUR smallint NOT NULL,
    LAEQ real NOT NULL,
    LA50 float NOT NULL,
    UNCERTAINTY smallint DEFAULT 255,
    VARIABILITY real DEFAULT 0,
    CONSTRAINT NOISECAPTURE_AREA_PROFILE_PK PRIMARY KEY (PK_AREA, HOUR),
    CONSTRAINT NOISECAPTURE_AREA_PROFILE_FK FOREIGN KEY (PK_AREA) REFERENCES NOISECAPTURE_AREA (PK_AREA) ON DELETE CASCADE
);

COMMENT ON COLUMN NOISECAPTURE_AREA_PROFILE.HOUR IS 'Hour of estimated value';
COMMENT ON COLUMN NOISECAPTURE_AREA_PROFILE.LAEQ IS 'Laeq on this hour';
COMMENT ON COLUMN NOISECAPTURE_AREA_PROFILE.LA50 IS 'LA50 on this hour';
COMMENT ON COLUMN NOISECAPTURE_AREA_PROFILE.UNCERTAINTY IS 'Uncertainty 0-255';
COMMENT ON COLUMN NOISECAPTURE_AREA_PROFILE.VARIABILITY IS 'Variability in dB(A)';

CREATE TABLE NOISECAPTURE_DUMP_TRACK_ENVELOPE(
    PK_TRACK int NOT NULL REFERENCES NOISECAPTURE_TRACK (PK_TRACK) ON DELETE CASCADE ON UPDATE CASCADE,
    THE_GEOM geometry,
    measure_count bigint);

-- Statistics cache table

CREATE TABLE noisecapture_stats_last_tracks (
    pk_track integer,
    time_length double precision,
    record_utc timestamptz,
    the_geom varchar,
    env varchar,
    start_pt varchar,
    stop_pt varchar,
    name_0 varchar,
    name_1 varchar,
    name_3 varchar,
    pk_party integer
);

--- Add index

CREATE INDEX ki_noisecapture_area_cellq
  ON noisecapture_area(cell_q);
CREATE INDEX ki_noisecapture_area_cellr
  ON noisecapture_area(cell_r);

CREATE INDEX fki_noisecapture_track_pk_user_fk
  ON noisecapture_track(pk_user);

CREATE INDEX fki_noisecapture_point_pk_track_fk
  ON noisecapture_point(pk_track);

CREATE INDEX fki_noisecapture_process_queue_pk_track_fk
  ON noisecapture_process_queue(pk_track);

CREATE INDEX fki_noisecapture_freq_pk_point_fk
  ON noisecapture_freq(pk_point);

-- H2GIS only queries

   CREATE SPATIAL INDEX ON NOISECAPTURE_POINT(THE_GEOM);
   CREATE SPATIAL INDEX ON NOISECAPTURE_AREA(THE_GEOM);
   CREATE SPATIAL INDEX ON NOISECAPTURE_AREA_CLUSTER(THE_GEOM);

 ---- PostGIS only query

 -- CREATE INDEX ON NOISECAPTURE_POINT USING GIST(THE_GEOM);
 -- CREATE INDEX ON NOISECAPTURE_AREA USING GIST(THE_GEOM);
 -- CREATE INDEX ON NOISECAPTURE_AREA_CLUSTER USING GIST(THE_GEOM);

 ---- Force SRID

-- SELECT UpdateGeometrySRID('noisecapture_dump_track_envelope','the_geom',4326);
-- SELECT UpdateGeometrySRID('noisecapture_area','the_geom',4326);
-- SELECT UpdateGeometrySRID('noisecapture_point','the_geom',4326);
-- SELECT UpdateGeometrySRID('noisecapture_area_cluster','the_geom',4326);
