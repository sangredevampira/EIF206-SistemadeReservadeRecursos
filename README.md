# Sistema de Reserva de Recursos (Proyecto 2)

EIF206 Programación III, UNA. Nueva versión del Proyecto 1 con arquitectura distribuida:

- **Backend** (`backend/`): servidor sin interfaz que escucha en un puerto TCP y atiende a cada cliente en su propio hilo.
  Tiene las capas Service, Lógica y Datos, y es el **único** proceso que se conecta a MySQL. Cuando alguien registra una
  reserva, avisa a los demás Frontend conectados (alertas).
- **Frontend** (`frontend/`): aplicación JavaFX con MVC (vistas FXML y controllers). No tiene ninguna referencia a la base de
  datos: usa proxies que envían las peticiones al Backend por una conexión TCP (patrón Proxy).
- **comun** (`comun/`): biblioteca que usan los dos procesos: DTO, interfaces de servicio y el protocolo de red (mensajes JSON).
  No es un proceso aparte.

| Integrante | Rol | Rama |
| --- | --- | --- |
| Derek García Herra | I1 · Base de datos, Maven, login, funcionarios, categorías y recursos | `Derek` |
| Kristel Peraza Sánchez | I2 · Servidor y concurrencia, reservas, calendarización y actividades | `Dev-Kris` |
| Sofía Delgado Salas | I3 · Cliente de red, ventana principal, alertas, estadísticas y PDF | `Sofia` |

## 1. Requisitos

- **JDK 26** (el proyecto compila con `maven.compiler.release` 26).
- **Maven 3** instalado, o el Maven que trae IntelliJ IDEA (ventana *Maven*). Maven debe usar el JDK 26.
- **MySQL 8 o superior** y **MySQL Workbench** para correr los scripts.
- JavaFX (26.0.2), Jackson, MySQL Connector/J (8.4.0), OpenPDF y JUnit **llegan solos por Maven**: no hay que instalarlos.

## 2. Estructura del repositorio

```
pom.xml      agregador: módulos comun, backend y frontend, con las versiones centralizadas
comun/       DTO, interfaces de servicio y protocolo de red (sin JavaFX ni JDBC)
backend/     servidor (Sistema.ServidorMain), Service, Lógica y Datos (JDBC); único con mysql-connector-j
frontend/    app JavaFX (Sistema.Main): vistas FXML, controllers, proxies, alertas, PDF e IA
bd/          scripts de la base de datos: 00_bases.sql, 01_schema.sql, 02_datos_iniciales.sql
docs/        documentación técnica del equipo
```

El proyecto no usa módulos de Java (`module-info.java`): varios paquetes están repartidos entre módulos y así deben quedar.

## 3. Base de datos

Los scripts están en `bd/` y se corren **en este orden**:

| Script | Dónde se corre | Qué hace |
| --- | --- | --- |
| `00_bases.sql` | Sin base seleccionada | Crea `reservas_db` (la del Backend) y `reservas_test` (la de las pruebas `*IT`), en `utf8mb4`, con `IF NOT EXISTS` |
| `01_schema.sql` | Una vez en `reservas_db` y otra en `reservas_test` | Crea las 6 tablas en la base seleccionada. **Empieza con `DROP TABLE IF EXISTS`: deja vacía esa base** |
| `02_datos_iniciales.sql` | **Solo en `reservas_db`**, justo después de `01` | Carga los datos de ejemplo (usuarios, funcionarios, categorías, recursos y reservas). En `reservas_test` no: las pruebas cargan sus propios datos |

Ningún script crea usuarios de MySQL ni contiene contraseñas.

**Cómo correrlos en MySQL Workbench:**

1. `File > Open SQL Script…` y abrir el script.
2. Para `01` y `02`: en el panel *SCHEMAS*, doble clic en la base para que quede en negrita y confirmar que es la correcta
   (`02` solo en `reservas_db`). `00` no necesita base seleccionada (si `reservas_db` no aparece después de correrlo, refrescar
   el panel *SCHEMAS*).
3. Ejecutar todo con el rayo (Ctrl+Shift+Enter).

**No usar `File > Run SQL Script`:** en MySQL Workbench 8.0 falla con errores internos (por ejemplo,
"'str' object has no attribute 'decode'") y llegó a crear una base con un nombre equivocado (`b'reservas_db'`).

Al correr `01` salen 3 advertencias 1681 "Integer display width is deprecated" por `TINYINT(1)`: son normales.

La collation `utf8mb4_0900_as_ci` no distingue mayúsculas pero sí tildes ("laptop" = "LAPTOP", "Camara" ≠ "Cámara"), y
también vale para los IDs (por ejemplo, `ADMIN` entra como `admin`).

## 4. Configuración

Hay tres archivos de configuración. **Ninguno se sube a Git** (están en `.gitignore`); en el repositorio solo están sus
`.example`. Se crean copiando el `.example` en la misma carpeta y quitándole `.example` al nombre.

### `backend/servidor.properties` (obligatorio para el servidor)

Copiar `backend/servidor.properties.example` como `backend/servidor.properties` y completar `bd.usuario` y `bd.clave` con el
usuario y la contraseña de MySQL de esa computadora:

```properties
puerto=5050
bd.url=jdbc:mysql://localhost:3306/reservas_db?characterEncoding=UTF-8&connectTimeout=5000
bd.usuario=root
bd.clave=ESCRIBA_AQUI_SU_CLAVE
```

- `puerto` es el puerto TCP donde escucha el servidor (5050 si falta).
- Si la contraseña tiene una barra invertida (`\`), hay que escribirla **doble** (`\\`): es la regla de los archivos
  `.properties` de Java.
- El archivo se lee en UTF-8. La clave puede estar vacía (`bd.clave=`) si MySQL no tiene contraseña, pero la línea debe existir.
- El servidor lo busca en `./servidor.properties`, `./backend/servidor.properties` y `../backend/servidor.properties`
  (según el directorio de trabajo); dejarlo en `backend/` funciona tanto desde la raíz como desde `backend/`.

### `backend/servidor-test.properties` (solo para las pruebas `*IT`)

Copiar `backend/servidor-test.properties.example` como `backend/servidor-test.properties` y completar `bd.usuario` y
`bd.clave`. Apunta a la base `reservas_test` y no lleva `puerto`. Solo lo usan las pruebas (Maven se lo indica con la
propiedad `servidor.config`, configurada en `backend/pom.xml`), así que las pruebas nunca tocan `reservas_db`. **Si no existe,
las `*IT` que usan MySQL se saltan** y el resto del build sigue normal. La regla de la barra invertida doble también aplica.

### `frontend/config.properties` (opcional)

Copiar `frontend/config.properties.example` como `frontend/config.properties`:

```properties
servidor.host=127.0.0.1
servidor.puerto=5050
```

- Si el archivo no existe, la app usa esos mismos valores (`127.0.0.1` y `5050`): para usar todo en una sola computadora no
  hace falta crearlo.
- La app lo busca en `frontend/config.properties` y, si no está, en `config.properties` del directorio de trabajo; dejarlo en
  `frontend/` funciona tanto al abrir la app desde la raíz como con `mvn -pl frontend javafx:run`.
- Solo hace falta cambiarlo cuando el servidor está en otra computadora (sección 7).

## 5. Compilar y probar

En la raíz del repositorio:

```bash
mvn clean install
```

Compila los tres módulos, corre todas las pruebas y los instala en el repositorio local de Maven (`.m2`). Hace falta antes de
abrir la app: `mvn -pl frontend javafx:run` usa la copia de `comun` instalada en `.m2`, así que después de cambiar código hay
que volver a correr `mvn clean install`.

Otros comandos útiles:

```bash
mvn clean verify               # compila y corre las pruebas, sin instalar
mvn install -DskipTests        # instala sin correr pruebas
```

Si `mvn` no está en el PATH, se puede usar la ventana *Maven* de IntelliJ (*Lifecycle* > `clean` y después `install`) o el
`mvn.cmd` que trae IntelliJ, con `JAVA_HOME` apuntando al JDK 26.

**Pruebas** (JUnit Jupiter):

- **Unidad (`*Test`)**, con Surefire: no necesitan MySQL ni el servidor encendido. Las de red levantan su propio servidor de
  prueba en un puerto libre.
- **Integración (`*IT`)**, con Failsafe (fase `verify`): usan MySQL real en `reservas_test`. Necesitan
  `backend/servidor-test.properties` y la base `reservas_test` con las tablas de `01_schema.sql`; si falta el archivo, se saltan.

**Mensajes que no son fallos** (si al final dice `BUILD SUCCESS`, todo pasó):

- Con Java 26, el aviso "A restricted method … has been called" (va por la salida de error; en PowerShell puede aparecer como
  `NativeCommandError`).
- El aviso de `sun.misc.Unsafe::invokeCleaner` de OpenPDF (la librería de PDF) durante `PdfReportServiceTest`.
- Dos `SEVERE` a propósito en pruebas del servidor: `ServidorReservasTest` y
  `ManejadorEstadisticasTest.ocultaDetallesInternosDeErroresInesperados` provocan un error interno para comprobar que el
  cliente no ve los detalles.

## 6. Ejecutar

**Siempre primero el servidor y después la app.** Antes: MySQL encendido, bases creadas (sección 3),
`backend/servidor.properties` listo (sección 4) y `mvn clean install` hecho (sección 5).

### 6.1 Servidor (Backend)

El proyecto **no tiene un comando de Maven para arrancar el servidor** (no hay `exec-maven-plugin` configurado). Se ejecuta la
clase `Sistema.ServidorMain` desde IntelliJ IDEA:

1. Abrir `backend/src/main/java/Sistema/ServidorMain.java`.
2. Ejecutarla con la flecha verde junto a `main` (*Run 'ServidorMain.main()'*).
3. Esperar el mensaje **"Servidor escuchando en el puerto 5050"** y dejarlo corriendo.
4. Para detenerlo: el cuadro rojo (*Stop*) de IntelliJ.

### 6.2 App (Frontend)

Con el servidor encendido, en la raíz del repositorio:

```bash
mvn -pl frontend javafx:run
```

O en la ventana *Maven* de IntelliJ: *frontend* > *Plugins* > *javafx* > `javafx:run`.

Para probar varios usuarios a la vez (por ejemplo, las alertas), se abre la app **varias veces**: todas se conectan al mismo
servidor.

No se abre con la flecha verde de `Sistema.Main`: así sale "JavaFX runtime components are missing", porque el proyecto no usa
módulos de Java. No es un error del proyecto: se usa `javafx:run`.

- **Sin servidor:** al arrancar, la app muestra el aviso "No se pudo conectar con el servidor." con los botones **Reintentar** y
  **Salir**. Se enciende el servidor y se pulsa Reintentar.
- **Si el servidor se reinicia con la app abierta**, la app no se reconecta sola: hay que cerrarla y volver a abrirla.

## 7. Varias computadoras

El servidor y las apps pueden estar en computadoras distintas:

1. Todas en la **misma red**.
2. En la computadora del servidor: MySQL, `backend/servidor.properties` y `ServidorMain` corriendo. Su IP se ve con `ipconfig`
   (*Dirección IPv4*).
3. En cada computadora con la app: crear `frontend/config.properties` con la IP del servidor en `servidor.host`
   (por ejemplo, `servidor.host=192.168.1.20`) y el mismo puerto en `servidor.puerto`. Estas computadoras no necesitan MySQL.
4. En el **Firewall de Windows de la computadora del servidor**, permitir conexiones entrantes **TCP al puerto 5050**
   (Windows puede preguntarlo la primera vez que el servidor abre el puerto; si no, se crea una regla de entrada en
   *Firewall de Windows Defender > Configuración avanzada > Reglas de entrada*).

Probado en dos computadoras del equipo:

- Marcar la red como **Privada** en la computadora del servidor (*Configuración > Red e Internet > Wi-Fi* o *Ethernet* >
  *Tipo de perfil de red*: *Privada*).
- Comando para abrir el puerto, en PowerShell **como administrador**:

  ```powershell
  New-NetFirewallRule -DisplayName "Reservas 5050" -Direction Inbound -Protocol TCP -LocalPort 5050 -Action Allow -Profile Private
  ```

- Comprobar desde la computadora de la app, antes de abrirla (debe decir `TcpTestSucceeded : True`):

  ```powershell
  Test-NetConnection IP_DEL_SERVIDOR -Port 5050
  ```

- Para quitar la regla después:

  ```powershell
  Remove-NetFirewallRule -DisplayName "Reservas 5050"
  ```

- **Aviso:** algunas redes (por ejemplo, de universidades) aíslan a los dispositivos entre sí; en ese caso usar otra red, como
  la de una casa o el hotspot de un celular.

## 8. Usuarios de prueba

Los carga `bd/02_datos_iniciales.sql` (son los del Proyecto 1; en la base se guardan como hash, nunca en claro):

| Usuario | Clave | Rol |
| --- | --- | --- |
| `admin` | `admin` | Administrador |
| `44444` | `44444` | Funcionario (Derek) |
| `123` | `1234` | Funcionario (Javier) |
| `77777` | `gigi` | Funcionario (Kristel) |
| `222222` | `222222` | Funcionario (Sofia) |

Un funcionario nuevo que registra el administrador entra con **clave = su ID**. La clave se puede cambiar con el botón "Cambiar" de
la pantalla de login o con "Cambiar Clave" dentro de la aplicación.

**Qué ve cada rol:**

- **Administrador:** pestañas Funcionarios, Categorías, Recursos, Calendarización, Actividades y Estadísticas.
- **Funcionario:** pestañas Reservas, Calendarización, Actividades y Estadísticas.
- **Los dos:** el panel de **Alertas** a la derecha, con las reservas que hacen otros usuarios mientras la sesión está abierta.

Las reservas de ejemplo son de junio a septiembre de 2026; ninguna es futura, así que la calendarización de las próximas
semanas sale vacía hasta que se crean reservas nuevas.

## 9. Función de IA (opcional)

En la pestaña Reservas (funcionario), el botón **Extraer AI** llena el formulario a partir de una frase, usando la API de
Gemini. Necesita la variable de entorno **`GEMINI_API_KEY`** en la computadora donde corre la app. Sin ella, todo lo demás
funciona igual y ese botón muestra "Error de IA".

La clave **nunca se escribe en el código ni se sube a Git**. En Windows se puede definir para el usuario con:

```powershell
setx GEMINI_API_KEY "SU_CLAVE_AQUI"
```

`setx` solo afecta a las ventanas que se abren después: hay que cerrar y volver a abrir la terminal o IntelliJ antes de abrir
la app.

## 10. Documentación del proyecto

- [`docs/00_contratos.md`](docs/00_contratos.md): referencia única del equipo: versiones, protocolo de red (mensajes JSON),
  todas las operaciones con sus datos, sesión, hilos del Frontend y reglas del Backend.
- [`docs/02_servidor_reservas.md`](docs/02_servidor_reservas.md): servidor TCP multicliente, sesión, reservas con candado y
  transacción, alertas, calendarización, actividades y concurrencia.

## 11. Problemas comunes

| Problema | Qué revisar |
| --- | --- |
| La app muestra "No se pudo conectar con el servidor." | Que `ServidorMain` esté corriendo; en otra computadora, la IP y el puerto de `frontend/config.properties`, la misma red y el puerto 5050 en el Firewall del servidor |
| "Error interno del servidor. Intente de nuevo." | La consola del servidor: casi siempre es MySQL apagado, `backend/servidor.properties` que falta o datos de conexión incorrectos. Corregir y reiniciar el servidor |
| "Error de conexión" en una app que ya estaba abierta, después de reiniciar el servidor | Cerrar y volver a abrir la app (no se reconecta sola) |
| "JavaFX runtime components are missing" | Abrir la app con `mvn -pl frontend javafx:run`, no con la flecha verde de `Main` |
| La app se comporta como antes de un cambio | Correr `mvn clean install` antes de `javafx:run` |
| Las `*IT` aparecen saltadas | Falta `backend/servidor-test.properties` (sección 4) |
| Error de acceso a MySQL con una contraseña con `\` | Escribir la barra invertida doble (`\\`) en el `.properties` |
| `File > Run SQL Script` falla en Workbench | Usar `File > Open SQL Script…` y el rayo (sección 3) |
| El servidor no arranca porque el puerto está ocupado | Ya hay otro `ServidorMain` abierto: detenerlo, o cambiar `puerto` en `servidor.properties` y `servidor.puerto` en `config.properties` |
| "Error de IA: El servicio de IA no respondió después de 3 intentos" | El servicio de Gemini respondió 503 (saturado) o tardó más de 30 s en cada intento. No es un error del proyecto: intentar de nuevo en unos minutos |
| "Error de IA" con `Error de la API Gemini (429)` y `RESOURCE_EXHAUSTED` | Se agotó la cuota diaria gratuita de la clave de Gemini; el mensaje dice cuándo se renueva. Si Gemini responde 503, cada clic en **Extraer AI** gasta hasta 3 solicitudes. No es un error del proyecto |
