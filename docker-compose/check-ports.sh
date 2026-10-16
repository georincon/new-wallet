#!/usr/bin/env bash
# Verifica que los puertos que publican los stacks de New Wallet (el unificado y el de la Registraduría) no
# choquen con otros servicios del PC (contenedores de otros proyectos o procesos del host). Los contenedores
# de nuestros propios proyectos NO cuentan como colisión (se recrean).
#   ./check-ports.sh          -> sale con código 1 si hay colisiones
#   ./check-ports.sh --fix    -> mueve a un puerto libre (escribiéndolo en el .env del stack) los puertos
#                                parametrizados (${VAR:-puerto}) que choquen, y vuelve a verificar
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
export NW_PROVIDER_PUBLIC_URL="${NW_PROVIDER_PUBLIC_URL:-http://localhost:8097}"
REG="../../eudi-srv-pid-registraduria/docker-compose"

python3 - "$REG" "${1:-}" <<'PY'
import json, os, subprocess, sys

reg = sys.argv[1]
fix = len(sys.argv) > 2 and sys.argv[2] == "--fix"
import re
stacks = [(".", "newwallet"), (reg, "registraduria")]          # (carpeta, nombre de proyecto)
own = {"newwallet", "registraduria", "newwallet-provider"}     # proyectos propios (incl. el provider suelto)

wanted = {}  # puerto -> "stack:servicio"
for folder, label in stacks:
    if not os.path.isdir(folder):
        continue
    cfg = json.loads(subprocess.check_output(["docker", "compose", "config", "--format", "json"],
                                             cwd=folder, text=True))
    for name, svc in cfg.get("services", {}).items():
        for p in svc.get("ports", []) or []:
            if p.get("published"):
                wanted[int(p["published"])] = f"{label}:{name}"

listening = set()
for line in subprocess.check_output(["ss", "-ltnH"], text=True).splitlines():
    cols = line.split()
    if len(cols) >= 4 and cols[3].rsplit(":", 1)[-1].isdigit():
        listening.add(int(cols[3].rsplit(":", 1)[-1]))

owners = {}
fmt = '{{.Names}}|{{.Label "com.docker.compose.project"}}|{{.Ports}}'
for line in subprocess.check_output(["docker", "ps", "--format", fmt], text=True).splitlines():
    name, project, ports = (line.split("|", 2) + ["", ""])[:3]
    for chunk in ports.split(","):
        if "->" in chunk:
            host = chunk.strip().split("->")[0].rsplit(":", 1)[-1]
            if host.isdigit():
                owners[int(host)] = (name, project)

def used_ports():
    u = set(listening)
    u.update(owners)
    return u

def compose_files(folder):
    return [os.path.join(folder, f) for f in ("docker-compose.yaml", "onboarding.yaml") if os.path.exists(os.path.join(folder, f))] + \
           [os.path.join("..", "backend", "wallet-provider", "docker", "docker-compose.yaml")] * (folder == ".")

def env_var_for(port):
    """(variable, carpeta del .env) si el puerto viene de una variable con valor por defecto en algun compose."""
    for folder, _ in stacks:
        for f in compose_files(folder):
            if os.path.exists(f):
                m = re.search(r"\$\{(\w+):-%d\}" % port, open(f).read())
                if m:
                    return m.group(1), folder
    return None, None

def write_env(folder, var, value):
    path = os.path.join(folder, ".env")
    lines = open(path).read().splitlines() if os.path.exists(path) else []
    lines = [l for l in lines if not l.startswith(var + "=")]
    lines.append(f"{var}={value}")
    open(path, "w").write("\n".join(lines) + "\n")

def find_collisions():
    out = []
    for port, who_wants in sorted(wanted.items()):
        if port in listening:
            name, project = owners.get(port, (None, None))
            if project in own:
                continue
            who = f"contenedor '{name}' (proyecto '{project or 'sin proyecto'}')" if name else "un proceso del host"
            out.append((port, who_wants, who))
    return out

collisions = find_collisions()
print("Puertos a publicar: " + ", ".join(f"{p}({w.split(':')[1]})" for p, w in sorted(wanted.items())))
unfixable = []
for port, w, who in collisions:
    var, folder = env_var_for(port) if fix else (None, None)
    if var:
        new_port = port + 1
        while new_port in used_ports() or new_port in wanted:
            new_port += 1
        write_env(folder, var, new_port)
        wanted[new_port] = wanted.pop(port)
        listening.add(new_port)
        print(f"  ~ :{port} ({w}) lo usa {who} -> reconfigurado a :{new_port} ({var} en {os.path.join(folder, '.env')})")
    else:
        unfixable.append((port, w, who))
if unfixable:
    print("\nCOLISIONES DE PUERTOS:")
    for port, w, who in unfixable:
        print(f"  - :{port} (lo necesita '{w}') ya lo usa {who}" + ("" if fix else "   [usa --fix para moverlo si es parametrizable]"))
    sys.exit(1)
print("OK: ningún puerto choca con otros servicios." if not collisions else "OK: puertos reconfigurados, sin colisiones.")
PY
