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
                Option                |                    Purpose                    |                    Type                    | Default
--------------------------------------|-----------------------------------------------|--------------------------------------------|--------
```query```                           | The query text to translate.                  | string                                     | Required
```fields.default``` or ```fields```  | The fields to query by default.               | fields string                              | Required
```default_operator```                | The default operator for two terms next to eachother. | ```"and"``` or ```"or"```          | ```"and"```
```empty```                           | Query to use on an empty query string         | ```"match_all"``` or ```"match_none"```    | ```"match_all"```
```allow_leading_wildcard```          | Is it ok if wildcard queries start with a wildcard? | boolean                              | ```false```
```fields.whitelist_defaults```       | Should the fields in the ```fields.default``` parameter be automatically whitelisted? | boolean | ```true```
```fields.whitelist_all```            | Should all not blacklisted fields be whitelisted? | boolean                                | ```false```
```fields.whitelist```                | Fields that can be queried.                   | list of strings                            | ```[]```
```fields.blacklist```                | Fields that can't be queried.                 | list of strings                            | ```[]```
```fields.aliases```                  | Aliases from one field name to one or more field names. | object with aliases as keys and fields string as values | ```{}```
```fields.definitions```              | Defines what actual fields are searched when searching a field name. | [object][definitions.format] | ```{}```


Note about ```fields.whitelist_defaults```: The default fields will be queried
even if they aren't whitelisted _but_ if this is true (and it is by default)
then users will be able to select all of these fields with the field:query
syntax.

Note about the format of ```fields.default``` and the values in the
```fields.aliases``` object: The "fields string" is of the format:
```"field(^boost)?(, ?field(^boost)?)*"```.

Field resolution
----------------
Field resolution is a staged process:
1. Parse the reference including the boost
2. Expand aliases
3. Remove fields that don't pass the whitelist or blacklist
4. Resolve the field definitions for each field

The takeaway from this is that the whitelist/blacklist process comes _after_
alias expansion and _before_ field definition substitution. Whitelisting the
"from" part of the alias does nothing, as does whitelisting the "quoted" or
"unquoted" parts of the field definitions.

[definitions.format]: [docs/format_definitions.md]
