#!/usr/bin/env python3
"""Sanity-check Android resources without the SDK.

  * every res/**/*.xml and the manifest is well-formed XML
  * every @type/name reference resolves to a defined resource
  * every R.type.name reference in Java resolves to a defined resource
  * every activity in the manifest exists as a Java class

Also emits (with --r-stub PATH) a fake R.java for offline javac type-checking.
"""

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "app" / "src" / "main"
RES = MAIN / "res"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

VALUE_TAGS = {
    "string": "string", "color": "color", "dimen": "dimen", "bool": "bool",
    "integer": "integer", "style": "style", "string-array": "array",
    "integer-array": "array", "array": "array", "plurals": "plurals",
    "attr": "attr",
}


def collect_defined():
    defined = {}  # type -> set(names)

    def add(rtype, name):
        defined.setdefault(rtype, set()).add(name.replace(".", "_"))

    for f in sorted(RES.rglob("*")):
        if not f.is_file():
            continue
        folder = f.parent.name.split("-")[0]
        stem = f.name.split(".")[0]
        if folder in ("drawable", "layout", "menu", "mipmap", "xml", "raw", "anim", "animator"):
            add(folder, stem)
        elif folder == "values" and f.suffix == ".xml":
            tree = ET.parse(f)  # raises on malformed XML
            for el in tree.getroot():
                rtype = VALUE_TAGS.get(el.tag)
                name = el.get("name")
                if rtype and name:
                    add(rtype, name)
    # ids declared inline with @+id/
    for f in sorted(RES.rglob("*.xml")):
        for m in re.finditer(r'"@\+id/([A-Za-z0-9_]+)"', f.read_text(encoding="utf-8")):
            add("id", m.group(1))
    return defined


def check_xml_wellformed(errors):
    for f in sorted(MAIN.rglob("*.xml")):
        try:
            ET.parse(f)
        except ET.ParseError as e:
            errors.append(f"malformed XML {f.relative_to(ROOT)}: {e}")


def check_references(defined, errors):
    ref_re = re.compile(r'"@(\+?)([a-z-]+)/([A-Za-z0-9_.]+)"')
    for f in sorted(MAIN.rglob("*.xml")):
        text = f.read_text(encoding="utf-8")
        for m in ref_re.finditer(text):
            plus, rtype, name = m.group(1), m.group(2), m.group(3)
            if plus or rtype.startswith("android:") or "android:" in m.group(0):
                continue
            if rtype in ("null",):
                continue
            key = name.replace(".", "_")
            if key not in defined.get(rtype, set()):
                errors.append(f"{f.relative_to(ROOT)}: unresolved @{rtype}/{name}")

    java_re = re.compile(r"(?<!android\.)\bR\.([a-z]+)\.([A-Za-z0-9_]+)")
    for f in sorted((MAIN / "java").rglob("*.java")):
        text = f.read_text(encoding="utf-8")
        for m in java_re.finditer(text):
            rtype, name = m.group(1), m.group(2)
            if name not in defined.get(rtype, set()):
                errors.append(f"{f.relative_to(ROOT)}: unresolved R.{rtype}.{name}")


def check_manifest_activities(errors):
    tree = ET.parse(MAIN / "AndroidManifest.xml")
    app = tree.getroot().find("application")
    names = [app.get(ANDROID_NS + "name")] if app is not None else []
    for el in app.iter() if app is not None else []:
        if el.tag in ("activity", "service", "receiver", "provider"):
            names.append(el.get(ANDROID_NS + "name"))
    for name in names:
        if not name:
            continue
        fqcn = ("io.github.roviicc.colordict" + name) if name.startswith(".") else name
        src = MAIN / "java" / Path(*fqcn.split(".")).with_suffix(".java")
        if not src.exists():
            errors.append(f"AndroidManifest.xml: missing class {fqcn}")


def emit_r_stub(defined, path: Path):
    lines = ["package io.github.roviicc.colordict;", "",
             "/** Offline stub for javac type-checking only — NOT compiled into the app. */",
             "public final class R {", "    private R() {}"]
    value = 0x7F000000
    for rtype in sorted(defined):
        lines.append(f"    public static final class {rtype} {{")
        for name in sorted(defined[rtype]):
            value += 1
            lines.append(f"        public static final int {name} = 0x{value:08X};")
        lines.append("    }")
    lines.append("}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"R stub written to {path}")


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--r-stub", type=Path, help="also write a fake R.java here")
    args = ap.parse_args(argv)

    errors = []
    check_xml_wellformed(errors)
    defined = collect_defined()
    check_references(defined, errors)
    check_manifest_activities(errors)

    for e in errors:
        print("ERROR:", e)
    if args.r_stub:
        emit_r_stub(defined, args.r_stub)
    if errors:
        return 1
    total = sum(len(v) for v in defined.values())
    print(f"resources OK ({total} resources across {len(defined)} types)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
