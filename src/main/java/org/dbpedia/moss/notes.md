
#

moss: https://dataid.dbpedia.org/ns/moss#

## Properties

### moss:layerName
A string, unique for each layer type

### moss:content
A uri, identifier of the content graph

### moss:extends
A uri, databus resource identifier



moss:DatabusMetadataLayer
moss:content



/*
{
    "@context": "https://raw.githubusercontent.com/dbpedia/databus-moss/dev/devenv/context2.jsonld",
    "@id": "%LAYER_ID%",
    "@type": "DatabusMetadataLayer",
    "layerName": "%LAYER_NAME%",
    "extends": "%DATABUS_RESOURCE%",
    "created": "%CREATED_DATE%",
    "modified": "%UPDATED_DATE%",
    "moss:content" : "%CONTENT_DOCUMENT%" 
 }
 */