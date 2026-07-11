# Databus MOSS Server

You can find deployment instructions and the Databus MOSS Frontend via the [main repo](https://github.com/dbpedia/databus-moss).

## Installation

See the [installation guide](doc/deployment.md) for setup and deployment.

## Usage

### CURL

To create entries on a MOSS instance first create an API key via `POST /users/me/api-keys`. Then call `POST /entries` with query parameters `module` and `resource`. The list of available modules can be requested via `GET /modules`. The `resource` parameter specifies the Databus resource being extended by the entry.

#### Example:
```
curl --request POST \
  --url 'https://moss.dev.dbpedia.link/entries?module=keyword&resource=https%3A%2F%2Fdatabus.dbpedia.org%2Fdbpedia-enterprise%2Fenriched-source-snapshots' \
  --header 'Content-Type: text/turtle' \
  --header 'X-API-KEY: [API_KEY_GOES_HERE]' \
  --data 'PREFIX schema: <https://schema.org/> 

<https://databus.dbpedia.org/dbpedia-enterprise/enriched-source-snapshots> 
    schema:keywords "simple" . '
```