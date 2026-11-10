#!/usr/bin/env python3
"""Check the local integrity of the OpenAPI document: references, path parameters, operation ids."""
from pathlib import Path
import re
import sys

import yaml

root = Path(__file__).resolve().parent.parent
api = yaml.safe_load((root / 'api-contract/openapi.yaml').read_text())
errors = []
schemas = api.get('components', {}).get('schemas', {})
operations = []
canonical_paths = {}
for path, path_item in api.get('paths', {}).items():
    canonical = re.sub(r'\{[^{}]+\}', '{}', path)
    if canonical in canonical_paths:
        errors.append(f'Equivalent templated paths: {path} and {canonical_paths[canonical]}')
    canonical_paths[canonical] = path
    for method, op in path_item.items():
        if method not in {'get', 'post', 'put', 'patch', 'delete', 'options', 'head', 'trace'}:
            continue
        operations.append(op)
        expected = set(re.findall(r'\{([^{}]+)\}', path))
        path_params = [p for p in op.get('parameters', []) if p.get('in') == 'path']
        if expected != {p['name'] for p in path_params} or any(not p.get('required') for p in path_params):
            errors.append(f'Invalid path parameters: {method.upper()} {path}')
        params = [(p['in'], p['name']) for p in op.get('parameters', [])]
        if len(params) != len(set(params)):
            errors.append(f'Duplicate parameters: {method.upper()} {path}')
        if not op.get('responses'):
            errors.append(f'Missing responses: {method.upper()} {path}')
ids = [op.get('operationId') for op in operations]
if None in ids or len(ids) != len(set(ids)):
    errors.append('Missing or duplicate operationId')
def walk(value):
    if isinstance(value, dict):
        ref = value.get('$ref')
        if ref and ref.startswith('#/'):
            target = api
            for part in ref[2:].split('/'):
                part = part.replace('~1', '/').replace('~0', '~')
                if not isinstance(target, dict) or part not in target:
                    errors.append(f'Unresolved reference: {ref}')
                    break
                target = target[part]
        for child in value.values():
            walk(child)
    elif isinstance(value, list):
        for child in value:
            walk(child)
walk(api)
if errors:
    print('\n'.join(errors), file=sys.stderr)
    sys.exit(1)
print(f"OK: {len(operations)} operations, {len(api['paths'])} paths, {len(schemas)} schemas; references checked")
