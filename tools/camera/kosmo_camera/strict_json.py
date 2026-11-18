"""Strict JSON helpers shared by the file readers (CV25, CV16, CV26): stdlib only.

Used as `json.loads(text, object_pairs_hook=no_duplicates, parse_constant=reject_constant)`, then `depth(root)` against
the file's own limit. The hooks raise `StrictJsonError`; each reader turns it into its own error type.
"""


class StrictJsonError(ValueError):
    """A duplicate key or a non-finite constant (NaN, Infinity) in a JSON text."""


def no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise StrictJsonError(f"$: duplicate key '{key}'")
        result[key] = value
    return result


def reject_constant(name):
    raise StrictJsonError(f"$: invalid number {name}")


def depth(value):
    """Nesting depth, computed iteratively so a hostile file cannot exhaust the stack."""
    deepest = 0
    stack = [(value, 1)]
    while stack:
        item, level = stack.pop()
        if isinstance(item, (dict, list)):
            deepest = max(deepest, level)
            children = item.values() if isinstance(item, dict) else item
            stack.extend((child, level + 1) for child in children)
    return deepest
