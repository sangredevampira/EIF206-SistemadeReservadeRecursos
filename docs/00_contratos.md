# Contratos del Proyecto 2

Sistema de Reserva de Recursos · Frontend + Backend + MySQL.
Referencia única entre Derek, Kristel y Sofía. Cualquier cambio se acuerda entre los tres y se anota aquí.
Si este documento y `desiciones.pdf` dicen cosas distintas, se corrige aquí y se avisa al equipo.

## 1. Versiones y configuración

| Tema | Acuerdo |
|---|---|
| Java / JavaFX | Java 26 y JavaFX 26.0.2 (si alguien no tiene Java 26, todos bajamos a Java 21) |
| Base de datos | MySQL 8, `mysql-connector-j` 8.4.0, codificación `utf8mb4` |
| Módulos Maven | `comun`, `backend`, `frontend` (migración de Derek). Lo nuevo va en estos módulos, no en `src/` |
| Puerto por defecto | `5050` |
| Backend | `backend/servidor.properties` con `puerto`, `bd.url`, `bd.usuario`, `bd.clave` |
| Pruebas Backend | `backend/servidor-test.properties` (base `reservas_test`) |
| Frontend | `config.properties` con host y puerto |
| Git | Los `.properties` reales no se suben; solo las versiones `.example` |

## 2. Protocolo de red

Un mensaje es **un objeto JSON en una sola línea**, codificado en UTF-8 y terminado con salto de línea.
Se usa Jackson con `JavaTimeModule` y `WRITE_DATES_AS_TIMESTAMPS` desactivado en ambos procesos.

- Fechas: `AAAA-MM-DD` (ejemplo `2026-11-03`).
- Horas: `HH:mm:ss` al escribir; al leer también se acepta `HH:mm`.
- Los campos sin valor se omiten del JSON (por ejemplo, un evento no lleva `idSolicitud` ni `exito`).
- Los nombres de operación son constantes compartidas en `comun` (`Sistema.Red.Operaciones`); deben ser idénticos en proxies y servidor.

### 2.1 SOLICITUD (Frontend → Backend)

```json
{"tipo":"SOLICITUD","idSolicitud":"3f1c...","operacion":"RES_CREAR","datos":{...}}
```

| Campo | Descripción |
|---|---|
| `tipo` | Siempre `SOLICITUD` |
| `idSolicitud` | UUID generado por `ClienteRed`; el servidor lo devuelve sin cambios |
| `operacion` | Una de las operaciones de la sección 3 |
| `datos` | Objeto con los parámetros, o `null` |

### 2.2 RESPUESTA (Backend → Frontend)

```json
{"tipo":"RESPUESTA","idSolicitud":"3f1c...","operacion":"RES_CREAR","exito":true,"mensaje":"","datos":{...}}
```

| Campo | Descripción |
|---|---|
| `idSolicitud` | El mismo de la solicitud (así `ClienteRed` empareja la respuesta) |
| `exito` | `true` si la operación se realizó |
| `mensaje` | Texto legible para el usuario cuando `exito=false`; nunca trazas ni contraseñas |
| `datos` | Resultado, o `null` |

Reglas de errores:

1. Todo error se responde como `RESPUESTA` con `exito=false` y un `mensaje` legible.
   `ClienteRed` también tolera `tipo = "ERROR"`, pero el Backend no lo usa.
2. El Backend valida toda solicitud (operación desconocida, `datos` ausentes, sesión no iniciada, JSON inválido).
3. El Proxy convierte `exito=false` por regla de negocio en `IllegalArgumentException(mensaje)`, que es lo que ya esperan
   los controladores heredados. Un fallo de conexión o de tiempo límite se presenta como error de conexión.
4. El tiempo límite de una solicitud es de 10 segundos (lo controla `ClienteRed`).
5. En el Backend, un fallo de regla de negocio se señala con `IllegalArgumentException("mensaje legible")`.
   El despachador la atrapa y responde `exito=false` con ese mensaje y el mismo `idSolicitud`.
   Si un manejador devuelve un `ResultadoOperacionDTO` con `exito=false`, el despachador lo responde igual como error.
6. Cualquier otra excepción se registra en el servidor y se responde `exito=false` con un mensaje genérico;
   la conexión sigue abierta. Los errores de SQL nunca viajan al cliente (la capa Datos los convierte en
   `IllegalStateException`, no en `IllegalArgumentException`).

### 2.3 EVENTO (Backend → Frontend)

```json
{"tipo":"EVENTO","operacion":"RESERVA_CREADA","datos":{ ...ReservaDTO completo... }}
```

- Los eventos no llevan `idSolicitud`.
- `RESERVA_CREADA` se envía solo a las conexiones **autenticadas** y **excluye la conexión que hizo la reserva**.
- `datos` es el `ReservaDTO` completo, con la lista de recursos y el nombre del funcionario.
  `ProcesadorAlertaReserva` lo lee con `FAIL_ON_UNKNOWN_PROPERTIES` activo, así que el JSON no debe llevar campos que `ReservaDTO` no tenga.
- Tras `AUTH_LOGOUT` la conexión deja de recibir eventos, aunque el socket siga abierto.

## 3. Operaciones

La columna "Define" indica quién fija el detalle de `datos`. Si una operación nueva hace falta, su dueño la agrega aquí y
pide a Kristel registrar el manejador.

| Módulo | Operación | Datos de entrada | Datos de salida | Define |
|---|---|---|---|---|
| Sesión (manejador: Kristel) | `AUTH_LOGIN` | idUsuario, clave | `LoginResultadoDTO` (`idUsuario`, `rol`, `funcionario`; `funcionario` es `null` para el administrador) | Derek |
| Sesión | `AUTH_CAMBIAR_CLAVE` | idUsuario, clave actual, clave nueva | — | Derek |
| Sesión | `AUTH_LOGOUT` | — | — | Derek / Kristel |
| Funcionarios | `FUNC_CREAR`, `FUNC_EDITAR`, `FUNC_BAJA`, `FUNC_BUSCAR`, `FUNC_LISTAR` | por definir | por definir | Derek |
| Categorías | `CAT_CREAR`, `CAT_EDITAR`, `CAT_ELIMINAR`, `CAT_BUSCAR`, `CAT_LISTAR` | por definir | por definir | Derek |
| Recursos | `REC_CREAR`, `REC_EDITAR`, `REC_ELIMINAR`, `REC_BUSCAR`, `REC_LISTAR`, `REC_NUEVO_ID` | por definir | por definir | Derek |
| Reservas | `RES_CREAR` | `idFuncionario`, `nombreFuncionario`, `nombreActividad`, `fecha`, `horaInicio`, `horaFin`, `idsCategorias` (lista) | `ReservaDTO` creado | Kristel |
| Reservas | `RES_CANCELAR` | `idReserva` | `true` si se canceló | Kristel |
| Reservas | `RES_BUSCAR_ID` | `idReserva` | `ReservaDTO` o `null` | Kristel |
| Reservas | `RES_BUSCAR_FUNCIONARIO` | `idFuncionario` | lista de `ReservaDTO` | Kristel |
| Reservas | `RES_BUSCAR_PERIODO` | `fechaDesde`, `fechaHasta` | lista de `ReservaDTO` | Kristel |
| Consulta | `CAL_CONSULTAR` | `idCategoria`, `fecha` | matriz de disponibilidad (`DisponibilidadRecursoDTO`) | Kristel (al implementar I2-C) |
| Consulta | `ACT_CONSULTAR` | rango de fechas | actividades programadas (`ActividadProgramadaDTO`) | Kristel (al implementar I2-D) |
| Consulta | `EST_RECURSOS`, `EST_ACTIVIDADES` | por definir | por definir | Sofía |
| Evento | `RESERVA_CREADA` | — (solo sale del Backend) | `ReservaDTO` completo | Kristel |

Manejadores: Kristel escribe el despachador, `ContextoSesion` y los manejadores de `AUTH_*`, `RES_*`, `CAL_*` y `ACT_*`.
Cada dueño escribe el manejador de su módulo (`FUNC_*`, `CAT_*`, `REC_*`, `EST_*`) con la interfaz `Manejador`
(sección 3.1) y Kristel lo registra.

### 3.1 Cómo es un manejador

```java
@FunctionalInterface
public interface Manejador {
    // datos: el objeto "datos" de la solicitud (puede ser null).
    // Devuelve lo que viaja en "datos" de la RESPUESTA (o null).
    // Para fallar: lanzar IllegalArgumentException("mensaje legible").
    Object manejar(JsonNode datos, ContextoSesion contexto);
}
```

`ContextoSesion` (uno por conexión): `estaAutenticado()`, `getIdUsuario()`, `getRol()`, `getFuncionario()`,
`exigirAdministrador()` (lanza `IllegalArgumentException` si no es administrador), `iniciarSesion(LoginResultadoDTO)`
y `cerrarSesion()`. Registro: `despachador.registrar(Operaciones.REC_LISTAR, manejador)`.

Notas sobre reservas:

- Cuando `RES_CREAR` falla por falta de disponibilidad, el `mensaje` indica **qué categorías no estaban disponibles**
  (el texto lo arma la Lógica del Backend).
- Solo se difunde `RESERVA_CREADA` cuando la reserva se guardó correctamente.

## 4. Sesión

- `ClienteRed` es único y compartido; se conecta al arrancar, antes del login.
- El Backend guarda en el contexto de cada conexión el `LoginResultadoDTO` recibido en `AUTH_LOGIN`.
  El Frontend lo guarda en `SessionManager`.
- Antes del login el Backend solo atiende `AUTH_LOGIN` y `AUTH_CAMBIAR_CLAVE` (esta última valida identificador y clave actual).
- Un funcionario inactivo no puede iniciar sesión.
- Si el servidor está apagado al abrir el Frontend, se muestra un mensaje con **Reintentar** y **Salir**.

## 5. Hilos en el Frontend

| En segundo plano (`TareaRed`) | Directo (hilo de JavaFX) |
|---|---|
| Autocompletado con IA | Guardar y borrar |
| Calendarización, actividades y estadísticas | Buscar y filtrar |
| Carga inicial de cada pestaña al iniciar sesión | Login y cambio de clave |

Las alertas llegan con `Platform.runLater`.

## 6. Reglas de Backend: datos, conexiones y concurrencia

1. `ConexionBD.obtenerConexion()` devuelve una conexión nueva en cada llamada. Quien la abre la cierra (`try-with-resources`).
2. Las transacciones las controla la **Lógica**: abre la conexión, apaga el autocommit, llama a varios métodos de **Datos**
   con la misma conexión y termina con `commit` o `rollback`.
3. Un método de Datos que **recibe** la conexión no la cierra (solo su `PreparedStatement` y `ResultSet`).
   Un método que no la recibe abre y cierra la suya.
4. `registrarReserva` es una sección crítica con `static final Object CANDADO_RESERVAS` (sin `private`) en `ReservaLogica`.
   Dentro del candado: buscar el primer recurso libre por categoría, generar el ID y guardar.
   El guardado de `reserva` y `reserva_recurso` va en una sola transacción.
5. `tieneReservasFuturas(idFuncionario)` y `tieneReservasFuturasActivas(idRecurso)` pasan a MySQL.
   Cada una tiene una versión que recibe la `Connection` (no la cierra) y otra que abre la suya.
   `RecursoLogica.filtrarRecursos` se llama sin conexión: es solo lectura y las bajas usan el mismo candado.
5.1. Las bajas de recursos y funcionarios que revisan reservas futuras usan el mismo candado:
   `synchronized (ReservaLogica.CANDADO_RESERVAS) { ... }`.
6. Los IDs con formato `PREFIJO-000001` salen de `GeneradorIdsBD.siguienteId(conexion, tabla, columnaId, prefijo)`;
   tabla y columna son constantes del código, nunca datos del usuario.
7. Las listas y búsquedas de recursos devuelven **solo recursos activos**. `RecursoLogica.filtrarRecursos(idCategoria)` conserva su firma.
   Las reservas existentes siguen mostrando sus recursos (lectura con `JOIN` desde `reserva_recurso`).
8. Bajas lógicas con el campo `activo` en `funcionario` y `recurso`. Una categoría con recursos (aunque estén inactivos) no se puede eliminar.
9. `reserva.estado` es `ENUM('ACTIVA','CANCELADA')`, con los mismos valores que el enum `EstadoReserva`.
10. Usuario y funcionario comparten el mismo ID; las claves se guardan con `PBKDF2WithHmacSHA256` y sal aleatoria en `clave_hash`.

## 7. Pruebas y repositorio

- Pruebas de integración contra la base `reservas_test`. Las que prueban la red levantan el servidor dentro de la misma prueba, en un puerto libre.
- Un PR solo se une si compila y pasan las pruebas (`mvn clean verify`). Derek integra los PR hacia `main`, cada día.
- Verificación final: quitar la dependencia temporal `frontend → backend`.
- Nadie agrega `module-info.java`: `Sistema.DTO`, `Sistema.Service` y `Sistema.Red` están repartidos entre módulos
  y eso solo funciona sin el sistema de módulos de Java.

## 8. Responsables

| Pieza | Responsable |
|---|---|
| Migración, `pom.xml`, dependencia temporal (y quitarla) | Derek |
| Hash de claves, script SQL, `ConexionBD`, `GeneradorIdsBD`, `LoginResultadoDTO` | Derek |
| `docs/00_contratos.md`, protocolo, servidor, reservas (candado y transacción), calendario, actividades | Kristel |
| Usar el candado en las bajas de recursos y funcionarios | Derek |
| `ClienteRed`, `TareaRed`, `ProxyBase` | Sofía |
| Cambiar cada controlador a su proxy | Quien tenga esa pantalla |
