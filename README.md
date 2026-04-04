# POS Abarrotes

Sistema de Punto de Venta (POS) para tiendas de abarrotes, desarrollado en Java con Swing.

## Requisitos

- Java 17 o superior
- Maven 3.6+

## Cómo ejecutar

### Opción 1: Desde Maven
```bash
mvn package -q
java -jar target/pos-sistema-1.0-SNAPSHOT.jar
```

### Opción 2: Desde IntelliJ IDEA
1. Abrir el proyecto en IntelliJ IDEA
2. Ejecutar la clase `AbarrotesPos`

## Base de Datos

La aplicación usa **SQLite** como base de datos local. El archivo se crea automáticamente en:
```
data/posabarrotes.db
```

La estructura de tablas se inicializa automáticamente al primer arranque.

### Usuarios por defecto
| Usuario | Contraseña | Rol |
|---------|-----------|-----|
| admin   | 1234      | ADMIN |
| cajero  | 1234      | CAJERO |

### Cómo resetear la base de datos
Para empezar desde cero, simplemente elimine el archivo:
```
data/posabarrotes.db
```
La siguiente vez que ejecute la app, se recreará con los datos iniciales.

## Configuración

La ruta de la base de datos se puede cambiar en:
```
src/main/resources/app.properties
```

Clave disponible:
- `db.url` – URL JDBC de SQLite (por defecto: `jdbc:sqlite:data/posabarrotes.db`)

## Módulo de Caja Global

- Solo puede existir **una caja ABIERTA** a la vez en el sistema.
- Solo el rol **ADMIN** puede abrir o cerrar la caja.
- Al iniciar sesión como **ADMIN**, si no hay caja abierta, se ofrece la opción de abrirla con un monto inicial.
- Al iniciar sesión como **CAJERO**, si no hay caja abierta, se muestra un aviso informativo; el cobro queda bloqueado hasta que el administrador abra la caja.
- El botón **"Abrir Caja"** en el menú lateral (solo ADMIN) abre la caja global solicitando el monto inicial.
- El botón **"Cerrar Caja (Corte Z)"** en el menú lateral (solo ADMIN) cierra la caja activa, solicita el efectivo contado, calcula la diferencia y genera un PDF de corte en `Cortes/`.
- Las ventas registradas durante la caja abierta quedan auditadas por el usuario que las realizó.

## Carpetas generadas en tiempo de ejecución

| Carpeta | Contenido |
|---------|-----------|
| `data/` | Base de datos SQLite |
| `Tickets/` | PDFs de tickets de venta |
| `Cortes/` | PDFs de cortes de caja |
