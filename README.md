# elasticsearch-payload-score

```
# index
{
    "analysis": {
      "analyzer": {
        "payload_analyzer": {
          "type": "custom",
          "tokenizer": "payload_tokenizer",
          "filter": [
            "payload_filter"
          ]
        }
      },
      "tokenizer": {
        "payload_tokenizer": {
          "type": "whitespace",
          "max_token_length": 64
        }
      },
      "filter": {
        "payload_filter": {
          "type": "delimited_payload",
          "encoding": "float"
        }
      }
    }
  },
  "mappings": {
    "_doc": {
      "properties": {
        "key": {
          "type": "text",
          "analyzer": "payload_analyzer",
          "term_vector": "with_positions_offsets_payloads",
          "store": true
        }
      }
    }
  }
}
```

```
# document
{
  "doc_id": "1"
  "key": [
    "coke|3 juice|1.1 coke|1"
  ]
}
```

```
# search
{
  "query": {
    "function_score": {
      "query": {
        "match": {
          "key": "yellow"
        }
      },
      "functions": [
        {
          "script_score": {
            "script": {
                "source": "payload_score",
                "lang" : "irgroup",
                "params": {
                    "field": "key",
                    "term": "yellow"
                }
            }
          }
        }
      ]
    }
  }
}
```
