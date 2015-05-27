#!/bin/bash

set -e
function cleanup {
    echo
}
trap cleanup EXIT

curl -XDELETE "http://localhost:9200/qstest?pretty"
curl -XPOST "http://localhost:9200/qstest?pretty" -d'{
    "index" : {
        "analysis" : {
            "analyzer" : {
                "stem" : {
                    "tokenizer": "standard",
                    "filter": ["standard", "lowercase", "kstem"]
                },
                "standard_edge": {
                    "tokenizer": "standard",
                    "filter": ["standard", "lowercase", "edge"]
                }
            },
            "filter": {
                "edge": {
                    "type": "edgeNGram",
                    "max_gram": 50
                }
            }
        }
    }
}'
curl -XPUT http://localhost:9200/qstest/test/_mapping?pretty -d'{
    "properties": {
        "foo" : {
            "type": "string",
            "analyzer": "stem",
            "fields": {
                "precise": {
                    "type": "string",
                    "analyzer": "standard"
                },
                "prefix_precise": {
                    "type": "string",
                    "analyzer": "standard_edge"
                }
            }
        }
    }
}'
curl -XGET 'http://localhost:9200/_cluster/health?pretty=true&wait_for_status=yellow'


curl -XPUT 'localhost:9200/qstest/test/1?pretty' -d'{"foo": "I need stemming and stuff"}'
curl -XPOST http://localhost:9200/qstest/_refresh?pretty

echo -n "Searching with stemming..."
curl -s -XPOST 'localhost:9200/qstest/test/_search?pretty' -d '{
    "query": {
        "qsppp": {
            "query": "stem",
            "fields": "foo"
        }
    }
}' | grep '"_id" : "1"'
echo -n "Wildcard searching without stemming..."
curl -s -XPOST 'localhost:9200/qstest/test/_search?pretty' -d '{
    "query": {
        "qsppp": {
            "query": "stemmin?",
            "fields": "foo"
        }
    }
}' | grep '"_id" : "1"'
echo -n "Prefix using prefix_precise..."
curl -s -XPOST 'localhost:9200/qstest/test/_search?pretty' -d '{
    "query": {
        "qsppp": {
            "query": "stemmi*",
            "fields": "foo"
        }
    }
}' | grep '"_id" : "1"'
echo -n "Field specifying search without stemming..."
curl -s -XPOST 'localhost:9200/qstest/test/_search?pretty' -d '{
    "query": {
        "qsppp": {
            "query": "foo.precise:stemming",
            "fields": {
                "default": "foo",
                "whitelist": ["foo.precise"]
            }
        }
    }
}' | grep '"_id" : "1"'
echo -n "Field specifying search without stemming take 2..."
curl -s -XPOST 'localhost:9200/qstest/test/_search?pretty' -d '{
    "query": {
        "qsppp": {
            "query": "foo.precise:stem",
            "fields": {
                "default": "foo",
                "whitelist": ["foo.precise"]
            }
        }
    }
}' | grep '"total" : 0'

echo Victory!
