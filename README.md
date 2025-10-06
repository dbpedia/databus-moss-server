# Databus MOSS Server

You can find deployment instructions and the Databus MOSS Frontend via the [main repo](https://github.com/dbpedia/databus-moss).

## Usage

### CURL

To create entries on a MOSS instance first create an API using the MOSS web application. Then call `https://moss.dev.dbpedia.link/api/v1/save-entry` with request parameters module and resource. The list of available modules can be requested via `https://moss.dev.dbpedia.link/api/v1/modules`. The resource parameters specifies the Databus resource being extended by the entry.

#### Example:
```
curl --request POST \
  --url 'https://moss.dev.dbpedia.link/api/v1/save-entry?module=keyword&resource=https%3A%2F%2Fdatabus.dbpedia.org%2Fdbpedia-enterprise%2Fenriched-source-snapshots' \
  --header 'Content-Type: text/turtle' \
  --header 'X-API-KEY: [API_KEY_GOES_HERE]' \
  --data 'PREFIX schema: <https://schema.org/> 

<https://databus.dbpedia.org/dbpedia-enterprise/enriched-source-snapshots> 
    schema:keywords "simple" . '
```