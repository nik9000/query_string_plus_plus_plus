Query Parser Plus Plus Plus [![Build Status](https://integration.wikimedia.org/ci/buildStatus/icon?job=search-query_string_plus_plus_plus)](https://integration.wikimedia.org/ci/job/search-query_string_plus_plus_plus)
===========================

Query Parser Plus Plus Plus is a ```query_string``` alternative for
Elasticsearch with a features:
* No syntax returns an error. Everything just devoles into a simpler query.
* Fields can be indexed many ways queries should be rewritten to take advantage
of it. Wildcard searches automatically search in more precise fields and can
even search in specially analyzed fields that allow the search to be rewritten
as a term query.
* The user is allowed to specify different fields to search (just like
```query_string```) but which fields they are allowed to specify is controlled
by a whitelist.

Installation
------------

TODO fix me I'm totally wrong

| Extra Queries and Filters Plugin |  ElasticSearch  |
|----------------------------------|-----------------|
| master branch                    | 1.5.X           |


Once its released you can install it like so for Elasticsearch 1.5.x:
```bash
./bin/plugin --install org.wikimedia.search/query_string_plus_plus_plus/0.0.1
```
But for now you have to build it yourself.

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
```fields.definitions```              | Defines what actual fields are searched when searching a field name. | [object](docs/format_definitions.md) | ```{}```


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
