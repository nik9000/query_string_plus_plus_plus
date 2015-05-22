The ```fields.definitions``` parameter looks like:
```json
{
    "field_name": {
        "standard": "field_name",
        "precise": "field_name.precise",
        "reverse_precise": "field_name.reverse_precise",
        "prefix_precise": "field_name.prefix"
    }
}
```

Where ```field_name``` is the name of the field you are defining.

All fields in the object under ```field_name``` are optional. They default to
the format above but they are only used if the fields are actually defined in
the mapping. That means that if you define a field that "looks like" the
precise field or looks like the reverse_precise field it'll automatically be
where appropriate.
