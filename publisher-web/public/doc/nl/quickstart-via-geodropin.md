# Quickstart: Data publiceren

>Beschikt u over GeoPublisher met GeoDropIn dan kunt via GeoDropIn uw shapefiles eenvoudig uploaden en vervolgens publiceren via de professionele web services van GeoPublisher. Maakt u geen gebruik van GeoDropIn dan kunt u direct naar stap II gaan.

## Globale workflow:

I) [Shapefile uploaden via GeoDropIn (optioneel)](#shapefiles) 

**II) [Dataset aanmaken via GeoPublisher](#datasets)**

**III) [Laag aanmaken en (default) stijl toevoegen](#lagen)** 

**IV) [Service aanmaken](#services)**

**V) [Laag controleren via de GeoPublisher viewer en Publiceren](#publiceren)**

VI) [Stijl aanpassen via QGIS (optioneel)](#qgis)

## Stappen:
### <a name="shapefiles"></a> I) Shapefile uploaden via GeoDropIn (optioneel)

1. Zorg dat u een zip-file heeft klaarstaan waarin minimaal de bestanden [filenaam]<b>.shp</b>, [filenaam]<b>.dbf</b>  en [filenaam]<b>.shx</b> zijn opgenomen.
2. Start **GeoDropIn** via [https://geodropin.geopublisher.nl/index](https://geodropin.geopublisher.nl/index)
3. Login
4. Klik op **+ toevoegen**
5. Vul **titel**, **beschrijving** en **datum** in. Dit is de (minimaal vereiste) metadata.
6. Klik op **choose file** en selecteer uw shapefile (zip). Uw shapefile is toegevoegd aan de brongegevens in GeoPublisher.

###  <a name="datasets"></a> II) Dataset aanmaken via GeoPublisher

1. Klik in de **GeoPublisher** links bij "Databeheer" op **Brongegevens**. U ziet uw data in de lijst staan met datasets
2. Klik op de **+ knop** om een nieuwe dataset aan te maken
3. Pas eventueel de velden aan en kies bij "dataset" de zojuist toegevoegde dataset. U ziet twee extra tabbladen ("kolommen" en "filters"), deze zijn voor geavanceerd gebruik en kunt u overslaan.
4. Klik op **Opslaan**. De dataset wordt nu geïmporteerd. U kunt de voortgang controleren via het Dashboard.

### <a name="lagen"></a> III) Laag aanmaken

1. Klik links bij "Databeheer" op **Datasets**.
2. Klik bij uw dataset op de knop **+ nieuwe laag**
3. Vul de velden in. Bij trefwoorden vult u zelf gekozen trefwoorden in om de vindbaarheid van de laag te vergroten, klik op de **+ knop** om het trefwoord toe te voegen. 
4. Kies een stijl in dit geval een default style en klik op de **+ knop** om de stijl toe te voegen. Klik buiten keuzevenster om verder te gaan. Het aanpassen van de stijl wordt verderop in deze handleiding uitgelegd. Het aanzetten van tiling is voor geavanceerd gebruik en kunt u nu overslaan.
5. Klik op **Opslaan**.

### <a name="services"></a> IV) Service aanmaken

1. Klik links bij "Servicebeheer" op **Services**
2. Klik op de knop **maak een nieuwe service**
3. Vul de velden in. Dit is de metadata die ervoor zorgt dat uw service vindbaar is via het web.
4. Klik op **Voeg een laag toe** om uw laag WMS uit de vorige stap aan de service toe te voegen. Het maken van groepen is voor geavanceerd gebruik en kunt u nu overslaan.
5. Klik op **Opslaan**, uw service is aangemaakt, maar nog niet gepubliceerd. Dit gebeurt in de volgende stap.

### <a name="publiceren"></a> V) Laag controleren en services publiceren

1. Klik links bij "Servicebeheer" op **Lagen**.
2. Klik op de laag die u heeft toegevoegd.
3. Onderaan ziet u nu dat de "preview" knop beschikbaar is. Klik op de knop om de laag in preview modus (opent in nieuw tabblad) te bekijken)
4. Klik links bij "Servicebeheer" op **Services** en vervolgens op **Publiceren**.  

### <a name="qgis"></a> VI) Optioneel: stijl aanpassen via QGIS

> In deze stap ziet u hoe u met behulp van GGIS (desktop GIS) een stijl kunt aanpassen. QGIS is open source software die u gratis kunt downloaden en gebruiken (zie [http://www.qgis.org/nl/](http://www.qgis.org/nl/)).

1. Klik links bij Databeheer op **Datasets**.
2. Klik bij de eerder toegevoegde dataset op het downloadicon voor de metadata van de dataset. De bestandsbeschrijving wordt in een nieuwe tab geopend.
3. Klik op de tab/knop **Details**
4. Kopieer de URL achter OGC:WFS. 
5. Start QGIS en voeg onder **kaartlagen** een nieuwe **WFS laag** toe mbv de zojuist gekopieerde URL. 
6. Zorg dat "paneel lagen" open staat en open van daaruit het paneel voor opmaak van stijlen 
7. Selecteer een van de kolommen om je stijl op te baseren en gebruik bijvoorbeeld vervolgens een kleurverloop (of "willekeurige kleur")
8. Klik onderaan op Style opslaan als **SLD**.
9. Ga naar de **GeoPublisher** en klik links bij "servicebeheer" op **Stijlen**.
10. Klik op **Maak een nieuwe stijl ..**
11. Upload de stijl via de knop **choose file** en klik op **Gekozen stijl inladen** of copy paste de stijl via een texteditor
12. Geef aan of het om punten, lijnen, vlakken gaat en valideer eventueel de stijl tegen het schema
13. Klik op **stijl opslaan** (stijl wordt automatisch gevalideerd)
14. U kunt de stijl nu toepassen in de stap bij aanmaken/editen van een laag (<b>Kies een stijl</b>) (zie boven).  
15. **NB Publiceer de service opnieuw om de stijl te effectueren.** 
