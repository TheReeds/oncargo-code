import copy
import importlib.util
import os
import unittest

spec = importlib.util.spec_from_file_location("oc", os.path.join(os.path.dirname(__file__), "openapi-compat.py"))
oc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(oc)

BASE = {
    "paths": {
        "/a": {
            "get": {"parameters": [{"in": "query", "name": "q", "required": False, "schema": {"type": "string"}}]},
            "post": {},
        }
    },
    "components": {"schemas": {
        "A": {"type": "object", "required": ["id"], "properties": {
            "id": {"type": "string", "format": "uuid"},
            "kind": {"type": "string", "enum": ["X", "Y"]},
            "b": {"$ref": "#/components/schemas/B"},
        }},
        "B": {"type": "object", "properties": {"n": {"type": "integer"}}},
        "C": {"allOf": [{"$ref": "#/components/schemas/B"}, {"properties": {"m": {"type": "string"}}}]},
    }},
}


def check(mut):
    head = copy.deepcopy(BASE)
    mut(head)
    return oc.compare(BASE, head)


class T(unittest.TestCase):
    def test_same(self):
        self.assertEqual(oc.compare(BASE, copy.deepcopy(BASE)), ([], []))

    def test_breaking(self):
        cases = {
            "path": lambda h: h["paths"].pop("/a"),
            "method": lambda h: h["paths"]["/a"].pop("post"),
            "schema": lambda h: h["components"]["schemas"].pop("B"),
            "prop": lambda h: h["components"]["schemas"]["A"]["properties"].pop("kind"),
            "type": lambda h: h["components"]["schemas"]["A"]["properties"]["id"].update(format="int64"),
            "ref": lambda h: h["components"]["schemas"]["A"]["properties"]["b"].update({"$ref": "#/components/schemas/C"}),
            "required": lambda h: h["components"]["schemas"]["A"].update(required=["id", "kind"]),
            "enum": lambda h: h["components"]["schemas"]["A"]["properties"]["kind"].update(enum=["X"]),
            "param removed": lambda h: h["paths"]["/a"]["get"].update(parameters=[]),
            "param required": lambda h: h["paths"]["/a"]["get"]["parameters"][0].update(required=True),
            "new required param": lambda h: h["paths"]["/a"]["get"]["parameters"].append({"in": "query", "name": "z", "required": True, "schema": {"type": "string"}}),
            "allOf prop removed": lambda h: h["components"]["schemas"]["C"]["allOf"][1]["properties"].pop("m"),
        }
        for name, mut in cases.items():
            with self.subTest(name):
                self.assertTrue(check(mut)[0], name)

    def test_additions_ok(self):
        def mut(h):
            h["paths"]["/new"] = {"get": {}}
            h["paths"]["/a"]["put"] = {}
            h["components"]["schemas"]["N"] = {"type": "object"}
            h["components"]["schemas"]["A"]["properties"]["opt"] = {"type": "string"}
            h["components"]["schemas"]["A"]["properties"]["kind"]["enum"].append("Z")
            h["paths"]["/a"]["get"]["parameters"].append({"in": "query", "name": "o", "schema": {"type": "string"}})
        breaking, info = check(mut)
        self.assertEqual(breaking, [])
        self.assertGreaterEqual(len(info), 6)

    def test_key_order_irrelevant(self):
        head = {"components": dict(reversed(list(BASE["components"].items()))), "paths": BASE["paths"]}
        self.assertEqual(oc.compare(BASE, head), ([], []))


if __name__ == "__main__":
    unittest.main()
