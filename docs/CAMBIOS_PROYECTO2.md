# Cambios integrados en `Dev-Kris` — inicio del Proyecto 2

Este documento resume los cambios que ya están integrados en la rama `Dev-Kris`
desde que empezó la migración del Proyecto 1 (aplicación JavaFX monolítica con
archivos JSON) al Proyecto 2 (cliente-servidor con MySQL).

| Commit    | Fecha      | Descripción |
|-----------|------------|-------------|
| `cfbdd22` | 2026-10-26 | Migración a módulos Maven: `comun`, `backend` y `frontend` |
| `d521b25` | 2026-10-27 | Corrige `RecursoServiceTest`: las pruebas quedaban dentro de la clase interna |
| `5e57fc5` | 2026-10-27 | Agrega `ResultadoOperacionDTO`, `LoginResultadoDTO` e `IAuthService` en `comun` |
| `93579f7` | 2026-10-28 | Agrega scripts de la base de datos: `00_bases.sql` y `01_schema.sql` |
| `fe8dad4` | 2026-10-28 | Agrega `HashClave` (PBKDF2WithHmacSHA256) con sus pruebas y el generador de hashes |
| `d8b7cea` | 2026-10-28 | Agrega `02_datos_iniciales.sql` con los datos del Proyecto 1 |

---

## 1. Migración a módulos Maven (`cfbdd22`)

El proyecto pasó de un único módulo (`src/`) a un **POM agregador** con tres módulos.
La mayoría de los archivos solo se movieron de carpeta (renombres sin cambios de contenido).

```
pom.xml            ← agregador (packaging pom, groupId proyecto2, versión 2.0-SNAPSHOT)
├── comun/         ← DTO, interfaces de servicio y utilidades compartidas
├── backend/       ← capas Datos, Logica y Service (servidor)
└── frontend/      ← JavaFX: vistas FXML, controllers, PDF, IA y cliente de red
bd/                ← scripts SQL
docs/              ← documentación
```

### Reglas de cada módulo

| Módulo     | Contiene | Dependencias | Regla |
|------------|----------|--------------|-------|
| `comun`    | `Sistema.DTO.*` (Categoria, Recurso, Reserva, Funcionario, Usuario, Rol, EstadoReserva, EstadisticaCategoria), `Sistema.util.HorarioSistema` | Jackson | **Nunca** entra JavaFX ni JDBC |
| `backend`  | `Sistema.Datos.*`, `Sistema.Logica.*`, `Sistema.Service.*`, datos JSON en `backend/src/data/` | `comun`, `mysql-connector-j` 8.4.0 | Único módulo que se conecta a MySQL; sin JavaFX |
| `frontend` | `ui.*` (controllers), vistas FXML, imágenes, `Sistema.Red.*`, PDF, IA, `SessionManager` | `comun`, JavaFX 26.0.2, OpenPDF, **`backend` (temporal)** | Al terminar no debe depender de `backend` |

### POM raíz

- `dependencyManagement` centraliza versiones: Jackson 2.18.2, JUnit 5.10.2,
  MySQL Connector 8.4.0, JavaFX 26.0.2, OpenPDF 1.3.30. Los módulos declaran
  dependencias **sin** `<version>`.
- `maven.compiler.release` = 26.
- **Surefire** corre las pruebas unitarias (`*Test`) y excluye `*IT.java`.
- **Failsafe** corre las pruebas de integración (`*IT`) en `mvn verify`.

### Dependencia temporal `frontend → backend`

`frontend/pom.xml` incluye `backend` como **dependencia de transición** para que
los controllers sigan usando los `Service` locales mientras cada integrante los
reemplaza por su proxy de red. **Se elimina antes de la entrega**: si al quitarla
algo no compila, ese controller todavía usa el backend directamente.

### Ajustes de código en la migración

- `AuthService` (backend) ya **no inicia la sesión** del `SessionManager`, porque
  el backend no conoce la interfaz. Ahora lo hace `LoginController` (frontend)
  cuando el login es exitoso.
- Se eliminaron los archivos de relleno (`prueba1.java`, `prueba`) y el
  `Main`/`MainController` viejos de `src/`.
- `.gitignore`: se agregan `frontend/src/data/` y `.mcp.json`.

## 2. Corrección de `RecursoServiceTest` (`d521b25`)

Una llave `}` mal ubicada dejaba los métodos `@Test` **dentro de la clase interna
fake**, por lo que JUnit no los ejecutaba. Se cerró la clase interna en su lugar
correcto; ahora las pruebas pertenecen a `RecursoServiceTest` y sí corren.

## 3. Contratos compartidos en `comun` (`5e57fc5`)

Base para la comunicación cliente-servidor. Todos tienen constructor vacío y
getters/setters porque viajan como JSON (Jackson).

- **`ResultadoOperacionDTO`** (`exito`, `mensaje`): resultado genérico de
  registrar, modificar, eliminar o cambiar clave. Reemplaza las clases
  `Resultado*` anidadas del Proyecto 1. Fábricas: `exito()` y `fallo(mensaje)`.
- **`LoginResultadoDTO`** (`idUsuario`, `rol`, `funcionario`): datos de un login
  exitoso. **Nunca lleva la clave ni el hash**. `funcionario` es `null` para el
  administrador. Un login fallido no usa esta clase: el proxy lo convierte en
  `IllegalArgumentException`.
- **`IAuthService`**: `login`, `cambiarClave`, `logout`. Lo implementarán el
  `AuthService` del backend y el proxy del frontend (mensajes `AUTH_LOGIN`,
  `AUTH_CAMBIAR_CLAVE`, `AUTH_LOGOUT`).
- Pruebas: `LoginResultadoDTOTest` y `ResultadoOperacionDTOTest`.

## 4. Base de datos MySQL (`93579f7`, `d8b7cea`)

Los scripts están en `bd/` y se ejecutan en orden:

| Script | Dónde se corre | Qué hace |
|--------|----------------|----------|
| `00_bases.sql` | Cualquier conexión | Crea `reservas_db` (backend) y `reservas_test` (pruebas `*IT`) con `IF NOT EXISTS` |
| `01_schema.sql` | Una vez en `reservas_db` y otra en `reservas_test` | ⚠️ **Hace `DROP TABLE` y recrea las 6 tablas vacías** |
| `02_datos_iniciales.sql` | **Solo** en `reservas_db` | Carga los datos de ejemplo del Proyecto 1 |

Los scripts no tienen `USE` ni nombre de base: en MySQL Workbench se abre con
*File > Open SQL Script…*, se selecciona la base (doble clic, en negrita) y se
ejecuta con el rayo (Ctrl+Shift+Enter). **No** usar *File > Run SQL Script*
(falla en Workbench 8.0). Las 3 advertencias 1681 por `TINYINT(1)` son normales.
Ningún script crea usuarios de MySQL ni contiene contraseñas.

### Esquema (`01_schema.sql`)

Collation `utf8mb4_0900_as_ci`: no distingue mayúsculas pero sí tildes, igual
que `equalsIgnoreCase` del Proyecto 1.

| Tabla | Clave | Notas |
|-------|-------|-------|
| `usuario` | `id_usuario` | `clave_hash` (PBKDF2), `rol` ENUM(`ADMINISTRADOR`,`FUNCIONARIO`), `activo` |
| `funcionario` | `id_funcionario` (PK y FK a `usuario`) | Baja lógica con `activo = 0`; el admin no tiene funcionario |
| `categoria` | `id_categoria` | `descripcion` única, sin espacios al inicio/final; sin baja lógica |
| `recurso` | `id_recurso` | FK obligatoria a `categoria` (RESTRICT); baja lógica |
| `reserva` | `id_reserva` | FK a `funcionario`; `estado` ENUM(`ACTIVA`,`CANCELADA`); `CHECK hora_inicio < hora_fin`; índices `(fecha, estado)` y `(id_funcionario, fecha)` |
| `reserva_recurso` | `(id_reserva, id_recurso)` | CASCADE hacia `reserva`, RESTRICT hacia `recurso` |

Consecuencias de las FK: una categoría con recursos (aunque inactivos) no se
puede eliminar, y un recurso con reservas no se borra, se desactiva.

### Datos iniciales (`02_datos_iniciales.sql`)

Mismos IDs del Proyecto 1: 5 usuarios, 4 funcionarios, 7 categorías,
11 recursos, 11 reservas y 20 filas en `reserva_recurso` (al final del script
hay un `SELECT` comentado para verificar los conteos).

Credenciales de prueba (iguales al Proyecto 1): `admin/admin`, `44444/44444`,
`123/1234`, `77777/gigi`, `222222/222222`.

Limpiezas respecto a los JSON del Proyecto 1:

- Solo las 11 reservas de `main` (se descartan las 2 de prueba del 25/10).
- `RES-000001` y `RES-000003` pasan del funcionario inexistente `1111111` al `44444`.
- Cada recurso usa la categoría de `recursos.json`, no la copia guardada en la reserva.
- Se agregan los recursos `891234`, `654321` y `567890`, que usaban las reservas
  pero faltaban en `recursos.json`.
- Se quitan espacios finales (`"Laptop "`, `"Kristel "`).

## 5. Hash de claves: `HashClave` (`fe8dad4`)

`backend/src/main/java/Sistema/Logica/HashClave.java` — las claves ya no se
guardan en texto plano.

- Algoritmo **PBKDF2WithHmacSHA256**, sal aleatoria de 16 bytes (`SecureRandom`),
  hash de 32 bytes, **600 000 iteraciones** (recomendación OWASP).
- Formato guardado en `usuario.clave_hash`:
  `pbkdf2_sha256$<iteraciones>$<sal Base64>$<hash Base64>`. Las iteraciones se
  guardan para poder subirlas sin invalidar hashes viejos.
- `generar(clave)`: lanza `IllegalArgumentException` si la clave es nula o vacía.
- `verificar(clave, guardado)`: devuelve `false` (sin excepción) ante cualquier
  dato inválido; compara en tiempo constante (`MessageDigest.isEqual`); limita
  las iteraciones a 10 000 000 para que un dato dañado no trabe el login.
- **`HashClaveTest`**: clave correcta/incorrecta, distinción de mayúsculas,
  sal distinta en cada hash, formato, entradas inválidas y el vector de prueba
  del RFC 7914.
- **`GeneradorHashesDatosIniciales`** (en `src/test`, no entra al jar ni lo corre
  Surefire): imprime el `INSERT` de `usuario` usado en `02_datos_iniciales.sql`.
  Cada corrida da hashes distintos por la sal.

  ```
  mvn -pl backend test-compile
  java -cp "backend/target/classes;backend/target/test-classes" Sistema.Logica.GeneradorHashesDatosIniciales
  ```

---

## Pendiente / próximos pasos

- Implementar la capa de datos JDBC en `backend` contra `reservas_db`
  (actualmente sigue leyendo los JSON de `backend/src/data/`).
- Hacer que `AuthService` implemente `IAuthService` y use `HashClave`.
- Crear en el frontend los proxies de red de cada servicio y cambiar los controllers.
- Eliminar la dependencia temporal `frontend → backend` antes de la entrega.

## Cómo compilar y probar

```
mvn clean install        # compila los 3 módulos y corre las pruebas unitarias (*Test)
mvn verify               # además corre las pruebas de integración (*IT)
mvn -pl frontend javafx:run   # ejecuta la aplicación
```
