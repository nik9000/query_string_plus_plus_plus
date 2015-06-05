The ```regex``` parameter looks like:
```json
{
    "regex": {
        "max_expand": 5,
        "max_states_traced": 10000
    }
}
```

All of the options under ```regex``` come from the [Wikimedia-Extra](https://github.com/wikimedia/search-extra/blob/master/docs/source_regex.md)
plugin documentation. All options there are supported here except
* ```regex```: its specified in the query
* ```field```: its specified in the query
* ```load_from_source```: always true for now
* ```ngram_field```: its specified in the field definitions
* ```gram_size```: its specified in the field definitions

See the [field definitions](format_definitions.md) documentation to see how to
configure ```ngram_field``` and ```gram_size```.
