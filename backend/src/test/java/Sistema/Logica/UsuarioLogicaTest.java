package Sistema.Logica;

import Sistema.DTO.Funcionario;
import Sistema.DTO.LoginResultadoDTO;
import Sistema.DTO.ResultadoOperacionDTO;
import Sistema.DTO.Rol;
import Sistema.Datos.UsuarioDatos;
import Sistema.Datos.UsuarioRegistro;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de unidad de las reglas de UsuarioLogica, sin MySQL: usa un
 * UsuarioDatos falso con los datos en memoria. Los hashes de los usuarios se
 * generan con pocas iteraciones para que las pruebas vayan rapido.
 */
public class UsuarioLogicaTest {

    private static final int POCAS_ITERACIONES = 1000;

    /** NOTA:
     * Fake de UsuarioDatos: busca sin distinguir mayusculas (como la collation
     * de MySQL) y devuelve el ID tal como esta "guardado".
     */
    static class UsuarioDatosFake extends UsuarioDatos {

        final List<UsuarioRegistro> usuarios = new ArrayList<>();
        final List<Funcionario> funcionarios = new ArrayList<>();
        RuntimeException excepcionARetornar;
        boolean actualizarDevuelve = true;

        // los parametros que recibio
        String idBuscado;
        String idActualizado;
        String hashGuardado;

        @Override
        public UsuarioRegistro buscarPorId(String id) {
            idBuscado = id;
            if (excepcionARetornar != null) throw excepcionARetornar;
            return usuarios.stream().filter(u -> u.id().equalsIgnoreCase(id)).findFirst().orElse(null);
        }

        @Override
        public Funcionario buscarFuncionarioAsociado(String id) {
            return funcionarios.stream().filter(f -> f.getId().equals(id)).findFirst().orElse(null);
        }

        @Override
        public boolean actualizarClaveHash(String id, String claveHash) {
            idActualizado = id;
            hashGuardado = claveHash;
            return actualizarDevuelve;
        }

        void agregarUsuario(String id, String clave, Rol rol, boolean activo) {
            usuarios.add(new UsuarioRegistro(id, HashClave.generar(clave, POCAS_ITERACIONES), rol, activo));
        }
    }

    private UsuarioDatosFake datosConUsuariosDePrueba() {
        UsuarioDatosFake datos = new UsuarioDatosFake();
        datos.agregarUsuario("admin", "admin", Rol.ADMINISTRADOR, true);
        datos.agregarUsuario("123", "1234", Rol.FUNCIONARIO, true);
        datos.funcionarios.add(new Funcionario("123", "Javier", "5676584"));
        datos.agregarUsuario("555", "555", Rol.FUNCIONARIO, false);   // inactivo
        datos.agregarUsuario("999", "999", Rol.FUNCIONARIO, true);    // sin fila en funcionario
        return datos;
    }

    // --- login ---

    @Test
    void loginCorrectoDelAdministradorSinFuncionario() {
        UsuarioLogica logica = new UsuarioLogica(datosConUsuariosDePrueba());

        LoginResultadoDTO resultado = logica.iniciarSesion("admin", "admin");

        assertEquals("admin", resultado.getIdUsuario());
        assertEquals(Rol.ADMINISTRADOR, resultado.getRol());
        assertNull(resultado.getFuncionario());
    }

    @Test
    void loginCorrectoDeUnFuncionarioTraeSuFuncionario() {
        UsuarioLogica logica = new UsuarioLogica(datosConUsuariosDePrueba());

        LoginResultadoDTO resultado = logica.iniciarSesion("123", "1234");

        assertEquals("123", resultado.getIdUsuario());
        assertEquals(Rol.FUNCIONARIO, resultado.getRol());
        assertNotNull(resultado.getFuncionario());
        assertEquals("Javier", resultado.getFuncionario().getNombre());
    }

    @Test
    void devuelveElIdTalComoEstaGuardadoNoComoSeEscribio() {
        UsuarioLogica logica = new UsuarioLogica(datosConUsuariosDePrueba());

        LoginResultadoDTO resultado = logica.iniciarSesion("ADMIN", "admin");

        assertEquals("admin", resultado.getIdUsuario());
    }

    @Test
    void recortaLosEspaciosDelIdPeroNoDeLaClave() {
        UsuarioDatosFake datos = datosConUsuariosDePrueba();
        UsuarioLogica logica = new UsuarioLogica(datos);

        assertEquals("admin", logica.iniciarSesion("  admin  ", "admin").getIdUsuario());
        assertEquals("admin", datos.idBuscado);
        assertThrows(IllegalArgumentException.class, () -> logica.iniciarSesion("admin", " admin "));
    }

    @Test
    void claveIncorrectaDaElMensajeGenerico() {
        UsuarioLogica logica = new UsuarioLogica(datosConUsuariosDePrueba());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> logica.iniciarSesion("admin", "otra"));
        assertEquals("ID o clave incorrectos.", ex.getMessage());
    }

    @Test
    void usuarioInexistenteDaElMismoMensajeQueClaveIncorrecta() {
        UsuarioLogica logica = new UsuarioLogica(datosConUsuariosDePrueba());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> logica.iniciarSesion("noExiste", "algo"));
        assertEquals("ID o clave incorrectos.", ex.getMessage());
    }

    @Test
    void inactivoConLaClaveCorrectaDaElMensajeDeInactivo() {
        UsuarioLogica logica = new UsuarioLogica(datosConUsuariosDePrueba());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> logica.iniciarSesion("555", "555"));
        assertEquals("Su usuario está inactivo. Contacte al administrador.", ex.getMessage());
    }

    @Test
    void inactivoConLaClaveIncorrectaNoRevelaQueEstaInactivo() {
        UsuarioLogica logica = new UsuarioLogica(datosConUsuariosDePrueba());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> logica.iniciarSesion("555", "otra"));
        assertEquals("ID o clave incorrectos.", ex.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void idVacioLanzaExcepcion(String id) {
        UsuarioLogica logica = new UsuarioLogica(datosConUsuariosDePrueba());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> logica.iniciarSesion(id, "admin"));
        assertEquals("Debe ingresar el ID.", ex.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void claveVaciaLanzaExcepcion(String clave) {
        UsuarioLogica logica = new UsuarioLogica(datosConUsuariosDePrueba());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> logica.iniciarSesion("admin", clave));
        assertEquals("Debe ingresar la clave.", ex.getMessage());
    }

    @Test
    void funcionarioSinDatosDeFuncionarioNoEntra() {
        UsuarioLogica logica = new UsuarioLogica(datosConUsuariosDePrueba());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> logica.iniciarSesion("999", "999"));
        assertEquals("El usuario no tiene datos de funcionario. Contacte al administrador.", ex.getMessage());
    }

    @Test
    void unErrorDeLaBaseDeDatosSePropaga() {
        UsuarioDatosFake datos = datosConUsuariosDePrueba();
        datos.excepcionARetornar = new IllegalStateException("No se pudo acceder a la base de datos.");
        UsuarioLogica logica = new UsuarioLogica(datos);

        assertThrows(IllegalStateException.class, () -> logica.iniciarSesion("admin", "admin"));
    }

    // --- cambio de clave ---

    @Test
    void cambioDeClaveCorrectoGuardaUnHashDeLaClaveNueva() {
        UsuarioDatosFake datos = datosConUsuariosDePrueba();
        UsuarioLogica logica = new UsuarioLogica(datos);

        ResultadoOperacionDTO resultado = logica.cambiarClave(" ADMIN ", "admin", "nueva123", "nueva123");

        assertTrue(resultado.isExito());
        assertNull(resultado.getMensaje());
        assertEquals("admin", datos.idActualizado);           // el ID guardado, sin espacios
        assertNotEquals("nueva123", datos.hashGuardado);      // nunca la clave en texto plano
        assertTrue(HashClave.verificar("nueva123", datos.hashGuardado));
        assertFalse(HashClave.verificar("admin", datos.hashGuardado));
    }

    @Test
    void cambioConClaveActualIncorrectaNoGuardaNada() {
        UsuarioDatosFake datos = datosConUsuariosDePrueba();
        UsuarioLogica logica = new UsuarioLogica(datos);

        ResultadoOperacionDTO resultado = logica.cambiarClave("admin", "otra", "nueva123", "nueva123");

        assertFalse(resultado.isExito());
        assertEquals("ID o clave actual incorrectos.", resultado.getMensaje());
        assertNull(datos.hashGuardado);
    }

    @Test
    void cambioConUsuarioInexistenteDaElMismoMensaje() {
        UsuarioDatosFake datos = datosConUsuariosDePrueba();
        UsuarioLogica logica = new UsuarioLogica(datos);

        ResultadoOperacionDTO resultado = logica.cambiarClave("noExiste", "algo", "nueva123", "nueva123");

        assertFalse(resultado.isExito());
        assertEquals("ID o clave actual incorrectos.", resultado.getMensaje());
        assertNull(datos.hashGuardado);
    }

    @Test
    void cambioDeUnUsuarioInactivoSeRechaza() {
        UsuarioDatosFake datos = datosConUsuariosDePrueba();
        UsuarioLogica logica = new UsuarioLogica(datos);

        ResultadoOperacionDTO resultado = logica.cambiarClave("555", "555", "nueva123", "nueva123");

        assertFalse(resultado.isExito());
        assertEquals("Su usuario está inactivo. Contacte al administrador.", resultado.getMensaje());
        assertNull(datos.hashGuardado);
    }

    @Test
    void cambioConIdVacio() {
        ResultadoOperacionDTO resultado = new UsuarioLogica(datosConUsuariosDePrueba())
                .cambiarClave("  ", "admin", "nueva123", "nueva123");

        assertFalse(resultado.isExito());
        assertEquals("Debe indicar el usuario.", resultado.getMensaje());
    }

    @Test
    void cambioConClaveActualVacia() {
        ResultadoOperacionDTO resultado = new UsuarioLogica(datosConUsuariosDePrueba())
                .cambiarClave("admin", "", "nueva123", "nueva123");

        assertFalse(resultado.isExito());
        assertEquals("Debe ingresar la clave actual.", resultado.getMensaje());
    }

    @Test
    void cambioConClaveNuevaVacia() {
        ResultadoOperacionDTO resultado = new UsuarioLogica(datosConUsuariosDePrueba())
                .cambiarClave("admin", "admin", "", "");

        assertFalse(resultado.isExito());
        assertEquals("La clave nueva no puede estar vacía.", resultado.getMensaje());
    }

    @Test
    void cambioConConfirmacionDistinta() {
        ResultadoOperacionDTO resultado = new UsuarioLogica(datosConUsuariosDePrueba())
                .cambiarClave("admin", "admin", "nueva123", "otra");

        assertFalse(resultado.isExito());
        assertEquals("Las dos claves nuevas no coinciden.", resultado.getMensaje());
    }

    @Test
    void cambioCuandoLaBaseNoActualizaNingunaFila() {
        UsuarioDatosFake datos = datosConUsuariosDePrueba();
        datos.actualizarDevuelve = false;
        UsuarioLogica logica = new UsuarioLogica(datos);

        ResultadoOperacionDTO resultado = logica.cambiarClave("admin", "admin", "nueva123", "nueva123");

        assertFalse(resultado.isExito());
        assertEquals("No se pudo actualizar la clave. Intente de nuevo.", resultado.getMensaje());
    }
}
