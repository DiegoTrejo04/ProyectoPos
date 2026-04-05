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

### Tablas principales

| Tabla | Descripción |
|-------|-------------|
| `Usuarios` | Cuentas de acceso (ADMIN / CAJERO) |
| `Categorias` | Categorías de productos |
| `Productos` | Catálogo de productos con stock |
| `MovimientosInventario` | Auditoría de cambios de stock |
| `Cajas` | Registro de turnos de caja (apertura/cierre) |
| `FolioSecuencias` | Secuencias diarias para generación de folios |
| `Ventas` | Encabezado de cada venta (folio, fecha, cajero, total) |
| `VentaDetalle` | Líneas de detalle de cada venta |

## Caja Global (solo ADMIN)

- Solo el **ADMIN** puede abrir y cerrar la caja.
- Los cajeros pueden vender **únicamente** si hay una caja global abierta.
- Al cerrar caja se genera un **Corte Z** en PDF (carpeta `Cortes/`).

## Credenciales por defecto

| Usuario | Contraseña | Rol |
|---------|-----------|-----|
| `admin` | `1234` | ADMIN |
| `cajero` | `1234` | CAJERO |

---

## Historial de Ventas / Folio Diario / Ticket PDF

### Folio Diario Global
- Cada venta recibe un folio único con formato: **`YYYYMMDD-000123`**
- El consecutivo se reinicia cada día.
- La generación es transaccional (garantiza unicidad).

### Historial de Ventas
- Accesible desde el menú lateral **"Historial de Ventas"** (o `Alt+H`).
- **CAJERO**: ve solo sus propias ventas.
- **ADMIN**: ve todas las ventas del sistema.
- Filtros disponibles:
  - **Hoy** (vista por defecto)
  - **Últimos 7 días**
- Doble clic o botón **"Ver Detalle"** muestra el diálogo de detalle.

### Detalle de Venta
El diálogo muestra:
- Folio, fecha/hora, cajero, total
- Tabla con productos (nombre, cantidad, precio unitario, subtotal)
- **Botón "Copiar Folio"**: copia el folio al portapapeles.
- **Botón "Generar Ticket PDF"**: genera el ticket en `Tickets/Ticket_<FOLIO>.pdf`.

### Ticket PDF
- Formato angosto tipo recibo (tamaño carta reducido).
- Guardado en la carpeta `Tickets/` (se crea automáticamente si no existe).
- Nombre del archivo: `Ticket_<FOLIO>.pdf` (nunca sobrescribe otro ticket).
- Incluye: nombre de tienda, folio, fecha, cajero, tabla de productos y totales.

## Estructura de archivos generados

```
Tickets/
  Ticket_20260405-000001.pdf
  Ticket_20260405-000002.pdf
  ...
Cortes/
  CorteZ_20260405_183045.pdf
  ...
data/
  posabarrotes.db
```
