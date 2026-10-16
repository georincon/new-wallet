# docker-compose — levantar todo New Wallet

```bash
./up.sh      # PKI (si falta) + IP del PC + todos los servicios
./down.sh    # detener (./down.sh -v borra también los datos)
```

| Servicio | Contenedor | Puerto |
|---|---|---|
| Backend de onboarding (Spring Boot) | `nw-onboarding-backend` | **8195** (red local, lo usa el teléfono) |
| Visión: rostro + OCR (Python/OpenCV) | `nw-onboarding-biometrics` | **8200** (solo localhost) |
| PostgreSQL de onboarding | `nw-onboarding-postgres` | **5443** (solo localhost) |
| New Wallet Provider | `newwallet-provider` | 8097 (red local) |
| PostgreSQL del provider | `postgres-nwp` | 5439 |
| Lista de estado del provider | `status-list-nwp` | interno |
| Issuer PID Registraduría (+ proxy TLS) | `pid-issuer-reg`, `haproxy-reg` | **9543** (HTTPS, red local) |
| Keycloak de la Registraduría (con login automático por `issuer_state`) | `keycloak-reg` | vía 9543 `/idp` |
| PostgreSQL / lista de estado de la Registraduría | `postgres-reg`, `status-list-reg` | 5441 / interno |

- Independiente de Avance: no comparte contenedores, redes, claves ni puertos con la wallet de Avance.
- El onboarding se construye desde `../../addi/OnboardingAddi` (solo lectura, no se modifica).
- Puertos propios (8195/8200/5443, 8097/5439, 9543/5441): no chocan con Addi (8095/8100/5433) ni con los demás proyectos; `up.sh` lo verifica con `check-ports.sh` y aborta si algo choca.
- El issuer de la Registraduría se trabaja EN SU CARPETA (`../../eudi-srv-pid-registraduria`); es su propio stack/proyecto: `up.sh` lo levanta en su carpeta. El login automático se activa con `NW_APPLY_LOGIN_BINDING=1 ./up.sh`.

Compilar la app apuntando a este stack:
```bash
./gradlew :app:assembleDevDebug -PbackendHost=<IP del PC> -Pnw.walletprovider.url=http://<IP del PC>:8097 -Pnw.issuer.url=https://<IP del PC>:9543/pid-registraduria
```
