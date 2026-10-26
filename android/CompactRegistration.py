"""Keep generated dispatch registration in data tables to bound compiler cost."""
from pathlib import Path
import re
import sys


def compact(path):
    original = path.read_text(encoding="utf-8")
    pattern = r"  registrar->SetFunction\((0x[0-9A-Fa-f]+), (\w+)\);\n"
    pairs = re.findall(pattern, original)
    if not pairs:
        return 0
    first = original.index("  registrar->SetFunction(")
    last = original.rindex("  registrar->SetFunction(")
    end = original.index("\n", last) + 1
    expected = "".join(f"  registrar->SetFunction({address}, {function});\n" for address, function in pairs)
    if original[first:end] != expected:
        raise ValueError("Registration includes other statements; refusing to rewrite")
    table = """  // Preserve every address/function pair and its registration order.
  static const struct Entry {
    uint32_t address;
    PPCFunc* function;
  } entries[] = {
"""
    table += "".join(f"    {{ {address}, {function} }},\n" for address, function in pairs)
    table += """  };
  for (const auto& entry : entries) {
    registrar->SetFunction(entry.address, entry.function);
  }
"""
    result = original[:first] + table + original[end:]
    if re.findall(r"    \{ (0x[0-9A-Fa-f]+), (\w+) \},\n", result) != pairs:
        raise AssertionError("Registration identity/order changed")
    path.write_text(result, encoding="utf-8", newline="\n")
    return len(pairs)


if __name__ == "__main__":
    root = Path(sys.argv[1])
    for path in sorted(root.glob("*/fifastreet_register.cpp")):
        print(f"{path.parent.name}: {compact(path)} registrations compacted")
