fileID = fopen('src/test/resources/org/orbisgis/sos/capture_1000hz_16bits_44100hz_signed.raw','r');
data = fread(fileID,inf,'int16',0,"ieee-le");

RMS_AT_90dB = 2500;
DB_FS_REFERENCE = - (20 * log10(RMS_AT_90dB)) + 90;
refSoundPressure = 1 / power(10, DB_FS_REFERENCE / 20);

audio_len = 1;
audio_sampling = 44100;
ref_lvl = 20 * log10(sqrt(sum(power(data, 2))) / length(data)) + DB_FS_REFERENCE;
ref_lvl
sf = length(data);
sf2 = sf/2;
[b,a]=butter ( 1, 50 / sf2 );
filtered = filter(b,a,data);

clf
subplot ( columns ( filtered ), 1, 1)
plot(filtered(:,1),";Impulse response;")
subplot ( columns ( filtered ), 1, 2 )
plot(filtered(:,2),";25Hz response;")
subplot ( columns ( filtered ), 1, 3 )
plot(filtered(:,3),";50Hz response;")
subplot ( columns ( filtered ), 1, 4 )
plot(filtered(:,4),";100Hz response;")