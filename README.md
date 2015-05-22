Query Parser Plus Plus Plus [![Build Status](https://integration.wikimedia.org/ci/buildStatus/icon?job=search-query_string_plus_plus_plus)](https://integration.wikimedia.org/ci/job/search-query_string_plus_plus_plus)
===========================

TODO fill this is

Installation
------------

TODO fix me I'm totally wrong

| Extra Queries and Filters Plugin |  ElasticSearch  |
|----------------------------------|-----------------|
| 1.5.0, master branch             | 1.5.X           |
| 1.4.0 -> 1.4.1, 1.4 branch       | 1.4.X           |
| 1.3.0 -> 1.3.1, 1.3 branch       | 1.3.4 -> 1.3.X  |
| 0.0.1 -> 0.0.2                   | 1.3.2 -> 1.3.3  |

Install it like so for Elasticsearch 1.5.x:
```bash
./bin/plugin --install org.wikimedia.search/extra/1.5.0
```

Install it like so for Elasticsearch 1.4.x:
```bash
./bin/plugin --install org.wikimedia.search/extra/1.4.1
```

and for Elasticsearch 1.3.x:
```bash
./bin/plugin --install org.wikimedia.search/extra/1.3.1
```

Options
-------
         Option         |                    Purpose                    |                    Type                    | Default
------------------------|-----------------------------------------------|--------------------------------------------|--------
```query```             | The query text to translate.                  | string                                     | Required
```fields.default``` or ```fields``` | The fields to query by default.  | fields string                              | Required
```default_operator```  | The default operator for two terms next to eachother. | ```"and"``` or ```"or"```          | ```"and"```
```empty```             | Query to use on an empty query string         | ```"match_all"``` or ```"match_none"```    | ```"match_all"```
```fields.whitelist_defaults``` | Should the fields in the ```fields.default``` parameter be automatically whitelisted? | boolean | ```true```
```fields.whitelist_all``` | Should all not blacklisted fields be whitelisted? | boolean                             | ```false```
```fields.whitelist```  | Fields that can be queried.                   | list of strings                            | ```[]```
```fields.blacklist```  | Fields that can't be queried.                 | list of strings                            | ```[]```
```fields.aliases```    | Aliases from one field name to one or more field names. | object with aliases as keys and fields string as values | ```{}```


Note about ```fields.whitelist_defaults```: The default fields will be queried
even if they aren't whitelisted _but_ if this is true (and it is by default)
then users will be able to select all of these fields with the field:query
syntax.

Note about the format of ```fields.default``` and the values in the
```fields.aliases``` object: The "fields string" is of the format:
```"field(^boost)?(, ?field(^boost)?)*"```.
