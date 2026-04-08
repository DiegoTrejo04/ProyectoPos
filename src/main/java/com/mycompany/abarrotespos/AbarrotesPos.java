package com.mycompany.abarrotespos;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.HashMap;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

public class AbarrotesPos {

    public static Usuario sesionActual;
    public static Caja cajaActual;

    public static void main(String[] args) {
        try {
            com.formdev.flatlaf.FlatLightLaf.setup();
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> {
            if (DatabaseInitializer.initialize()) {
                new LoginDialog(null).setVisible(true);
            } else {
                JOptionPane.showMessageDialog(null, "Error critico inicializando la base de datos.\nVerifique que la carpeta 'data/' sea accesible.");
            }
        });
    }

    // ==========================================
    // 1. CONFIGURACION
    // ==========================================
    static class Config {
        static final Color COLOR_SIDEBAR = new Color(33, 43, 54);
        static final Color COLOR_BG = new Color(240, 242, 245);
        static final Color COLOR_CARD_BG = Color.WHITE;
        static final Color COLOR_PRIMARY = new Color(0, 123, 255);
        static final Color COLOR_SUCCESS = new Color(40, 167, 69);
        static final Color COLOR_WARNING = new Color(255, 193, 7);
        static final Color COLOR_DANGER = new Color(220, 53, 69);
        static final Color COLOR_INFO = new Color(23, 162, 184);
        static final Color COLOR_DARK = new Color(52, 58, 64);

        static final Font FONT_MAIN = new Font("Segoe UI", Font.PLAIN, 14);
        static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 16);
        static final Font FONT_BIG = new Font("Segoe UI", Font.BOLD, 24);
        static final Font FONT_HUGE = new Font("Segoe UI", Font.BOLD, 48);
        static final Font FONT_KPI_VALUE = new Font("Segoe UI", Font.BOLD, 28);
        static final Font FONT_KPI_TITLE = new Font("Segoe UI", Font.PLAIN, 14);
    }

    // ==========================================
    // 2. CONFIG SERVICE
    // ==========================================
    static class ConfigService {
        private static final Properties props = new Properties();
        static {
            try (InputStream in = ConfigService.class.getResourceAsStream("/app.properties")) {
                if (in != null) props.load(in);
            } catch (IOException e) { /* ignore */ }
        }
        public static String get(String key, String def) {
            return props.getProperty(key, def);
        }
        public static String getDbUrl() {
            return get("db.url", "jdbc:sqlite:data/posabarrotes.db");
        }
    }

    // ==========================================
    // 3. CONEXION DB
    // ==========================================
    public static class ConexionDB {
        private static ConexionDB instance;
        private Connection connection;
        private ConexionDB() {}
        public static synchronized ConexionDB getInstance() {
            if (instance == null) instance = new ConexionDB();
            return instance;
        }
        public Connection getConnection() throws SQLException {
            if (connection == null || connection.isClosed()) {
                try {
                    Class.forName("org.sqlite.JDBC");
                    String url = ConfigService.getDbUrl();
                    if (url.startsWith("jdbc:sqlite:")) {
                        String path = url.substring("jdbc:sqlite:".length());
                        java.io.File f = new java.io.File(path);
                        if (f.getParentFile() != null) f.getParentFile().mkdirs();
                    }
                    connection = DriverManager.getConnection(url);
                    try (Statement st = connection.createStatement()) {
                        st.execute("PRAGMA journal_mode=WAL");
                        st.execute("PRAGMA foreign_keys=ON");
                    }
                } catch (ClassNotFoundException e) {
                    throw new SQLException("Driver SQLite no encontrado.", e);
                }
            }
            return connection;
        }
    }

    // ==========================================
    // 4. DATABASE INITIALIZER
    // ==========================================
    static class DatabaseInitializer {
        public static boolean initialize() throws SQLException {
            Connection conn = ConexionDB.getInstance().getConnection();
            try (Statement stmt = conn.createStatement()) {

                stmt.execute("CREATE TABLE IF NOT EXISTS Categorias (" +
                    "IdCategoria INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "Nombre TEXT NOT NULL UNIQUE)");

                stmt.execute("CREATE TABLE IF NOT EXISTS Productos (" +
                    "IdProducto INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "CodigoBarras TEXT NOT NULL UNIQUE," +
                    "Nombre TEXT NOT NULL," +
                    "IdCategoria INTEGER NOT NULL," +
                    "Costo REAL NOT NULL DEFAULT 0," +
                    "PrecioVenta REAL NOT NULL DEFAULT 0," +
                    "StockActual INTEGER NOT NULL DEFAULT 0," +
                    "StockMinimo INTEGER NOT NULL DEFAULT 0," +
                    "Activo INTEGER NOT NULL DEFAULT 1," +
                    "FOREIGN KEY (IdCategoria) REFERENCES Categorias(IdCategoria))");

                stmt.execute("CREATE TABLE IF NOT EXISTS Usuarios (" +
                    "Id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "Username TEXT NOT NULL UNIQUE," +
                    "Password TEXT NOT NULL," +
                    "Rol TEXT NOT NULL DEFAULT 'CAJERO')");

                stmt.execute("CREATE TABLE IF NOT EXISTS MovimientosInventario (" +
                    "IdMovimiento INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "IdProducto INTEGER NOT NULL," +
                    "TipoMovimiento TEXT NOT NULL," +
                    "Cantidad INTEGER NOT NULL," +
                    "StockAnterior INTEGER NOT NULL DEFAULT 0," +
                    "StockNuevo INTEGER NOT NULL DEFAULT 0," +
                    "FechaMovimiento TEXT NOT NULL DEFAULT (datetime('now','localtime'))," +
                    "UsuarioResponsable TEXT," +
                    "FOREIGN KEY (IdProducto) REFERENCES Productos(IdProducto))");

                stmt.execute("CREATE TABLE IF NOT EXISTS Cajas (" +
                    "Id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "UsuarioAdminApertura TEXT NOT NULL," +
                    "FechaApertura TEXT NOT NULL DEFAULT (datetime('now','localtime'))," +
                    "MontoInicial REAL NOT NULL DEFAULT 0," +
                    "FechaCierre TEXT," +
                    "UsuarioAdminCierre TEXT," +
                    "MontoFinalEfectivo REAL," +
                    "TotalVentasCalculado REAL," +
                    "Diferencia REAL," +
                    "Estado TEXT NOT NULL DEFAULT 'ABIERTA')");

                stmt.execute("CREATE TABLE IF NOT EXISTS FolioSecuencias (" +
                    "Fecha TEXT PRIMARY KEY," +
                    "Ultimo INTEGER NOT NULL DEFAULT 0)");

                stmt.execute("CREATE TABLE IF NOT EXISTS Ventas (" +
                    "Id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "Folio TEXT NOT NULL UNIQUE," +
                    "Fecha TEXT NOT NULL DEFAULT (datetime('now','localtime'))," +
                    "UsuarioCajero TEXT NOT NULL," +
                    "Total REAL NOT NULL DEFAULT 0," +
                    "Estado TEXT NOT NULL DEFAULT 'COMPLETADA')");

                stmt.execute("CREATE TABLE IF NOT EXISTS VentaDetalle (" +
                    "Id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "VentaId INTEGER NOT NULL," +
                    "ProductoId INTEGER NOT NULL," +
                    "Cantidad INTEGER NOT NULL," +
                    "PrecioUnitario REAL NOT NULL," +
                    "Subtotal REAL NOT NULL," +
                    "FOREIGN KEY (VentaId) REFERENCES Ventas(Id)," +
                    "FOREIGN KEY (ProductoId) REFERENCES Productos(IdProducto))");

                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM Usuarios");
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("INSERT INTO Usuarios (Username, Password, Rol) VALUES ('admin', '1234', 'ADMIN')");
                    stmt.execute("INSERT INTO Usuarios (Username, Password, Rol) VALUES ('cajero', '1234', 'CAJERO')");
                }
                rs.close();

                rs = stmt.executeQuery("SELECT COUNT(*) FROM Categorias");
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("INSERT INTO Categorias (Nombre) VALUES ('Abarrotes')");
                    stmt.execute("INSERT INTO Categorias (Nombre) VALUES ('Bebidas')");
                    stmt.execute("INSERT INTO Categorias (Nombre) VALUES ('Lacteos')");
                    stmt.execute("INSERT INTO Categorias (Nombre) VALUES ('Limpieza')");
                    stmt.execute("INSERT INTO Categorias (Nombre) VALUES ('Botanas')");
                }
                rs.close();

                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    // ==========================================
    // 5. PDF SERVICE
    // ==========================================
    static class PdfService {
        public static void generarTicketPDF(List<DetalleVenta> carrito, double total, double pago, double cambio, String folio) {
            String nombreArchivo = "Tickets/Ticket_" + folio + ".pdf";
            crearDirectorio("Tickets");
            try {
                PdfWriter writer = new PdfWriter(nombreArchivo);
                PdfDocument pdf = new PdfDocument(writer);
                Document document = new Document(pdf);
                document.setMargins(20, 20, 20, 20);
                document.add(new Paragraph("TIENDA ABARROTES").setTextAlignment(TextAlignment.CENTER).setBold().setFontSize(16));
                document.add(new Paragraph("Ticket de Venta").setTextAlignment(TextAlignment.CENTER).setFontSize(11));
                document.add(new Paragraph("Folio: " + folio).setTextAlignment(TextAlignment.CENTER).setBold().setFontSize(10));
                document.add(new Paragraph("Fecha: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).setTextAlignment(TextAlignment.CENTER).setFontSize(9));
                if (AbarrotesPos.sesionActual != null)
                    document.add(new Paragraph("Cajero: " + AbarrotesPos.sesionActual.username).setTextAlignment(TextAlignment.CENTER).setFontSize(9));
                document.add(new Paragraph("------------------------------------------------").setTextAlignment(TextAlignment.CENTER).setFontSize(8));
                Table table = new Table(UnitValue.createPercentArray(new float[]{4, 1, 2}));
                table.setWidth(UnitValue.createPercentValue(100));
                table.addHeaderCell(new Cell().add(new Paragraph("Producto").setBold().setFontSize(9)));
                table.addHeaderCell(new Cell().add(new Paragraph("Cant").setBold().setFontSize(9)));
                table.addHeaderCell(new Cell().add(new Paragraph("Total").setBold().setFontSize(9)));
                for (DetalleVenta d : carrito) {
                    table.addCell(new Cell().add(new Paragraph(d.producto.nombre).setFontSize(9)));
                    table.addCell(new Cell().add(new Paragraph(String.valueOf(d.cantidad)).setFontSize(9)));
                    table.addCell(new Cell().add(new Paragraph(String.format("$%.2f", d.subtotal)).setFontSize(9)));
                }
                document.add(table);
                document.add(new Paragraph("------------------------------------------------").setTextAlignment(TextAlignment.CENTER).setFontSize(8));
                document.add(new Paragraph(String.format("TOTAL: $%.2f", total)).setTextAlignment(TextAlignment.RIGHT).setBold().setFontSize(12));
                document.add(new Paragraph(String.format("Efectivo: $%.2f", pago)).setTextAlignment(TextAlignment.RIGHT).setFontSize(9));
                document.add(new Paragraph(String.format("Cambio:  $%.2f", cambio)).setTextAlignment(TextAlignment.RIGHT).setFontSize(9));
                document.add(new Paragraph("\nGracias por su compra!").setTextAlignment(TextAlignment.CENTER).setFontSize(9));
                document.close();
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(new File(nombreArchivo));
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Error generando PDF: " + e.getMessage());
                e.printStackTrace();
            }
        }

        public static void generarTicketVentaPDF(Venta venta, List<DetalleVenta> detalles) {
            String nombreArchivo = "Tickets/Ticket_" + venta.folio + ".pdf";
            crearDirectorio("Tickets");
            try {
                PdfWriter writer = new PdfWriter(nombreArchivo);
                PdfDocument pdf = new PdfDocument(writer);
                Document document = new Document(pdf);
                document.setMargins(20, 20, 20, 20);
                document.add(new Paragraph("TIENDA ABARROTES").setTextAlignment(TextAlignment.CENTER).setBold().setFontSize(16));
                document.add(new Paragraph("Ticket de Venta").setTextAlignment(TextAlignment.CENTER).setFontSize(11));
                document.add(new Paragraph("Folio: " + venta.folio).setTextAlignment(TextAlignment.CENTER).setBold().setFontSize(10));
                document.add(new Paragraph("Fecha: " + venta.fecha).setTextAlignment(TextAlignment.CENTER).setFontSize(9));
                document.add(new Paragraph("Cajero: " + venta.usuarioCajero).setTextAlignment(TextAlignment.CENTER).setFontSize(9));
                document.add(new Paragraph("------------------------------------------------").setTextAlignment(TextAlignment.CENTER).setFontSize(8));
                Table table = new Table(UnitValue.createPercentArray(new float[]{4, 1, 2}));
                table.setWidth(UnitValue.createPercentValue(100));
                table.addHeaderCell(new Cell().add(new Paragraph("Producto").setBold().setFontSize(9)));
                table.addHeaderCell(new Cell().add(new Paragraph("Cant").setBold().setFontSize(9)));
                table.addHeaderCell(new Cell().add(new Paragraph("Total").setBold().setFontSize(9)));
                for (DetalleVenta d : detalles) {
                    table.addCell(new Cell().add(new Paragraph(d.producto.nombre).setFontSize(9)));
                    table.addCell(new Cell().add(new Paragraph(String.valueOf(d.cantidad)).setFontSize(9)));
                    table.addCell(new Cell().add(new Paragraph(String.format("$%.2f", d.subtotal)).setFontSize(9)));
                }
                document.add(table);
                document.add(new Paragraph("------------------------------------------------").setTextAlignment(TextAlignment.CENTER).setFontSize(8));
                document.add(new Paragraph(String.format("TOTAL: $%.2f", venta.total)).setTextAlignment(TextAlignment.RIGHT).setBold().setFontSize(12));
                document.add(new Paragraph("\nGracias por su compra!").setTextAlignment(TextAlignment.CENTER).setFontSize(9));
                document.add(new Paragraph("Estado: " + venta.estado).setTextAlignment(TextAlignment.CENTER).setFontSize(8));
                document.close();
                JOptionPane.showMessageDialog(null,
                    "Ticket PDF generado:\n" + nombreArchivo,
                    "Ticket Generado", JOptionPane.INFORMATION_MESSAGE);
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(new File(nombreArchivo));
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Error generando Ticket PDF: " + e.getMessage());
                e.printStackTrace();
            }
        }

        public static void generarCortePDF(Map<String, Object> stats) {
            String nombreArchivo = "Cortes/Corte_" + LocalDate.now() + ".pdf";
            crearDirectorio("Cortes");
            try {
                PdfWriter writer = new PdfWriter(nombreArchivo);
                PdfDocument pdf = new PdfDocument(writer);
                Document document = new Document(pdf);
                document.add(new Paragraph("CORTE DE CAJA (Z)").setTextAlignment(TextAlignment.CENTER).setBold().setFontSize(20));
                document.add(new Paragraph("Fecha: " + LocalDate.now()).setTextAlignment(TextAlignment.CENTER));
                document.add(new Paragraph("\n"));
                double ventas = (double) stats.getOrDefault("totalVentas", 0.0);
                int trans = (int) stats.getOrDefault("numTransacciones", 0);
                document.add(new Paragraph("Resumen del Día:").setBold());
                document.add(new Paragraph("Ventas Totales: $" + String.format("%.2f", ventas)));
                document.add(new Paragraph("Transacciones: " + trans));
                document.add(new Paragraph("\n\n"));
                document.add(new Paragraph("__________________________").setTextAlignment(TextAlignment.CENTER));
                document.add(new Paragraph("Firma del Cajero").setTextAlignment(TextAlignment.CENTER));
                document.close();
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(new File(nombreArchivo));
            } catch (Exception e) { e.printStackTrace(); }
        }

        public static void generarCorteCajaPDF(Caja caja) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String nombreArchivo = "Cortes/CorteZ_" + timestamp + ".pdf";
            crearDirectorio("Cortes");
            try {
                PdfWriter writer = new PdfWriter(nombreArchivo);
                PdfDocument pdf = new PdfDocument(writer);
                Document document = new Document(pdf);
                document.add(new Paragraph("CORTE DE CAJA GLOBAL (Z)").setTextAlignment(TextAlignment.CENTER).setBold().setFontSize(20));
                document.add(new Paragraph("Apertura: " + caja.fechaApertura).setTextAlignment(TextAlignment.CENTER));
                document.add(new Paragraph("Cierre: " + (caja.fechaCierre != null ? caja.fechaCierre : "N/A")).setTextAlignment(TextAlignment.CENTER));
                document.add(new Paragraph("Admin Apertura: " + caja.usuarioAdminApertura).setTextAlignment(TextAlignment.CENTER));
                document.add(new Paragraph("Admin Cierre: " + (caja.usuarioAdminCierre != null ? caja.usuarioAdminCierre : "N/A")).setTextAlignment(TextAlignment.CENTER));
                document.add(new Paragraph("\n"));
                document.add(new Paragraph("Monto Inicial: $" + String.format("%.2f", caja.montoInicial)));
                document.add(new Paragraph("Ventas del Turno: $" + String.format("%.2f", caja.totalVentasCalculado != null ? caja.totalVentasCalculado : 0.0)));
                document.add(new Paragraph("Efectivo Contado: $" + String.format("%.2f", caja.montoFinalEfectivo != null ? caja.montoFinalEfectivo : 0.0)));
                document.add(new Paragraph("Diferencia: $" + String.format("%.2f", caja.diferencia != null ? caja.diferencia : 0.0)).setBold());
                document.add(new Paragraph("\n\n"));
                document.add(new Paragraph("__________________________").setTextAlignment(TextAlignment.CENTER));
                document.add(new Paragraph("Admin: " + (caja.usuarioAdminCierre != null ? caja.usuarioAdminCierre : "")).setTextAlignment(TextAlignment.CENTER));
                document.close();
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(new File(nombreArchivo));
            } catch (Exception e) { e.printStackTrace(); }
        }

        private static void crearDirectorio(String ruta) {
            File directorio = new File(ruta);
            if (!directorio.exists()) directorio.mkdirs();
        }
    }

    // ==========================================
    // 6. MODELOS
    // ==========================================
    static class Usuario {
        int id; String username; String rol;
        public Usuario(int id, String u, String r) { this.id = id; this.username = u; this.rol = r; }
    }

    static class Categoria {
        int id; String nombre;
        public Categoria(int id, String nombre) { this.id = id; this.nombre = nombre; }
        @Override public String toString() { return nombre; }
    }

    static class Caja {
        int id;
        String usuarioAdminApertura;
        String fechaApertura;
        double montoInicial;
        String fechaCierre;
        String usuarioAdminCierre;
        Double montoFinalEfectivo;
        Double totalVentasCalculado;
        Double diferencia;
        String estado;

        public Caja(int id, String usuarioAdminApertura, String fechaApertura, double montoInicial,
                    String fechaCierre, String usuarioAdminCierre, Double montoFinalEfectivo,
                    Double totalVentasCalculado, Double diferencia, String estado) {
            this.id = id; this.usuarioAdminApertura = usuarioAdminApertura;
            this.fechaApertura = fechaApertura; this.montoInicial = montoInicial;
            this.fechaCierre = fechaCierre; this.usuarioAdminCierre = usuarioAdminCierre;
            this.montoFinalEfectivo = montoFinalEfectivo;
            this.totalVentasCalculado = totalVentasCalculado;
            this.diferencia = diferencia; this.estado = estado;
        }
    }

    static class Producto {
        int id, idCategoria, stock, stockMinimo;
        String codigoBarras, nombre, categoria;
        double costo, precio;
        public Producto(int id, String codigo, String nom, String cat, int idCat, double costo, double precio, int stock, int min) {
            this.id = id; this.codigoBarras = codigo; this.nombre = nom; this.categoria = cat;
            this.idCategoria = idCat; this.costo = costo; this.precio = precio;
            this.stock = stock; this.stockMinimo = min;
        }
        public Object[] toRow() { return new Object[]{id, codigoBarras, nombre, categoria, String.format("$%.2f", precio), stock}; }
    }

    static class DetalleVenta {
        Producto producto; int cantidad; double subtotal;
        public DetalleVenta(Producto p, int cant) { this.producto = p; this.cantidad = cant; recalcular(); }
        public void setCantidad(int c) { this.cantidad = c; recalcular(); }
        private void recalcular() { this.subtotal = producto.precio * cantidad; }
    }

    static class Venta {
        int id;
        String folio, fecha, usuarioCajero, estado;
        double total;
        public Venta(int id, String folio, String fecha, String usuarioCajero, double total, String estado) {
            this.id = id; this.folio = folio; this.fecha = fecha;
            this.usuarioCajero = usuarioCajero; this.total = total; this.estado = estado;
        }
    }

    static class DetalleVentaGuardado {
        int id, ventaId, productoId, cantidad;
        String productoNombre;
        double precioUnitario, subtotal;
        public DetalleVentaGuardado(int id, int ventaId, int productoId, String productoNombre,
                                     int cantidad, double precioUnitario, double subtotal) {
            this.id = id; this.ventaId = ventaId; this.productoId = productoId;
            this.productoNombre = productoNombre; this.cantidad = cantidad;
            this.precioUnitario = precioUnitario; this.subtotal = subtotal;
        }
    }

    // ==========================================
    // 7. DAOs
    // ==========================================
    static class UsuarioDAO {
        public static boolean inicializarTablaUsuarios() {
            return DatabaseInitializer.initialize();
        }
        public Usuario login(String user, String pass) throws SQLException {
            String sql = "SELECT * FROM Usuarios WHERE Username = ? AND Password = ?";
            Connection conn = ConexionDB.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, user); ps.setString(2, pass);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return new Usuario(rs.getInt("Id"), rs.getString("Username"), rs.getString("Rol"));
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return null;
        }
    }

    // ==========================================
    // 7b. FOLIO SERVICE
    // ==========================================
    static class FolioService {
        public static synchronized String generarFolio() throws SQLException {
            String fechaHoy = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            Connection conn = ConexionDB.getInstance().getConnection();
            boolean prevAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT OR IGNORE INTO FolioSecuencias (Fecha, Ultimo) VALUES (?, 0)")) {
                    ins.setString(1, fechaHoy);
                    ins.executeUpdate();
                }
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE FolioSecuencias SET Ultimo = Ultimo + 1 WHERE Fecha = ?")) {
                    upd.setString(1, fechaHoy);
                    upd.executeUpdate();
                }
                int consecutivo;
                try (PreparedStatement sel = conn.prepareStatement(
                        "SELECT Ultimo FROM FolioSecuencias WHERE Fecha = ?")) {
                    sel.setString(1, fechaHoy);
                    try (ResultSet rs = sel.executeQuery()) {
                        if (!rs.next()) throw new SQLException("No se pudo obtener el folio.");
                        consecutivo = rs.getInt(1);
                    }
                }
                conn.commit();
                return fechaHoy + "-" + String.format("%06d", consecutivo);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(prevAutoCommit);
            }
        }
    }

    // ==========================================
    // 7c. VENTA DAO
    // ==========================================
    static class VentaDAO {
        public synchronized Venta registrarVenta(List<DetalleVenta> carrito, double total, String usuarioCajero) throws SQLException {
            String folio = FolioService.generarFolio();
            Connection conn = ConexionDB.getInstance().getConnection();
            boolean prevAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                int ventaId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO Ventas (Folio, Fecha, UsuarioCajero, Total, Estado) VALUES (?, datetime('now','localtime'), ?, ?, 'COMPLETADA')",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, folio);
                    ps.setString(2, usuarioCajero);
                    ps.setDouble(3, total);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) throw new SQLException("No se pudo insertar la venta.");
                        ventaId = rs.getInt(1);
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO VentaDetalle (VentaId, ProductoId, Cantidad, PrecioUnitario, Subtotal) VALUES (?, ?, ?, ?, ?)")) {
                    for (DetalleVenta d : carrito) {
                        ps.setInt(1, ventaId);
                        ps.setInt(2, d.producto.id);
                        ps.setInt(3, d.cantidad);
                        ps.setDouble(4, d.producto.precio);
                        ps.setDouble(5, d.subtotal);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
                return obtenerVentaPorId(ventaId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(prevAutoCommit);
            }
        }

        public Venta obtenerVentaPorId(int id) throws SQLException {
            Connection conn = ConexionDB.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM Ventas WHERE Id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapVenta(rs);
                }
            }
            return null;
        }

        public List<Venta> obtenerVentasHoy(String usuarioCajero) throws SQLException {
            String sql = "ADMIN".equals(usuarioCajero)
                ? "SELECT * FROM Ventas WHERE date(Fecha) = date('now','localtime') ORDER BY Id DESC"
                : "SELECT * FROM Ventas WHERE date(Fecha) = date('now','localtime') AND UsuarioCajero = ? ORDER BY Id DESC";
            return ejecutarConsultaVentas(sql, "ADMIN".equals(usuarioCajero) ? null : usuarioCajero);
        }

        public List<Venta> obtenerVentasUltimos7Dias(String usuarioCajero) throws SQLException {
            String sql = "ADMIN".equals(usuarioCajero)
                ? "SELECT * FROM Ventas WHERE Fecha >= datetime('now','-7 days','localtime') ORDER BY Id DESC"
                : "SELECT * FROM Ventas WHERE Fecha >= datetime('now','-7 days','localtime') AND UsuarioCajero = ? ORDER BY Id DESC";
            return ejecutarConsultaVentas(sql, "ADMIN".equals(usuarioCajero) ? null : usuarioCajero);
        }

        private List<Venta> ejecutarConsultaVentas(String sql, String usuarioCajero) throws SQLException {
            List<Venta> lista = new ArrayList<>();
            Connection conn = ConexionDB.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (usuarioCajero != null) ps.setString(1, usuarioCajero);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) lista.add(mapVenta(rs));
                }
            }
            return lista;
        }

        public List<DetalleVentaGuardado> obtenerDetalleVenta(int ventaId) throws SQLException {
            List<DetalleVentaGuardado> lista = new ArrayList<>();
            String sql = "SELECT vd.*, p.Nombre as ProductoNombre FROM VentaDetalle vd " +
                "JOIN Productos p ON vd.ProductoId = p.IdProducto WHERE vd.VentaId = ?";
            Connection conn = ConexionDB.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, ventaId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lista.add(new DetalleVentaGuardado(
                            rs.getInt("Id"), rs.getInt("VentaId"), rs.getInt("ProductoId"),
                            rs.getString("ProductoNombre"), rs.getInt("Cantidad"),
                            rs.getDouble("PrecioUnitario"), rs.getDouble("Subtotal")));
                    }
                }
            }
            return lista;
        }

        private Venta mapVenta(ResultSet rs) throws SQLException {
            return new Venta(rs.getInt("Id"), rs.getString("Folio"), rs.getString("Fecha"),
                rs.getString("UsuarioCajero"), rs.getDouble("Total"), rs.getString("Estado"));
        }
    }

    static class CajaDAO {
        public Caja obtenerCajaAbiertaGlobal() throws SQLException {
            String sql = "SELECT * FROM Cajas WHERE Estado = 'ABIERTA' ORDER BY Id DESC LIMIT 1";
            Connection conn = ConexionDB.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapCaja(rs);
            }
            return null;
        }

        public Caja abrirCaja(String adminUsername, double montoInicial) throws SQLException {
            if (obtenerCajaAbiertaGlobal() != null) {
                throw new SQLException("Ya existe una caja abierta. Ciérrela primero.");
            }
            String sql = "INSERT INTO Cajas (UsuarioAdminApertura, MontoInicial, Estado) VALUES (?, ?, 'ABIERTA')";
            Connection conn = ConexionDB.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, adminUsername);
                ps.setDouble(2, montoInicial);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return obtenerCajaPorId(rs.getInt(1));
                }
            }
            return null;
        }

        public Caja obtenerCajaPorId(int id) throws SQLException {
            String sql = "SELECT * FROM Cajas WHERE Id = ?";
            Connection conn = ConexionDB.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapCaja(rs);
                }
            }
            return null;
        }

        public double calcularTotalVentasCaja(Caja caja) throws SQLException {
            String sql = "SELECT COALESCE(SUM(Total), 0) as Total FROM Ventas WHERE Estado = 'COMPLETADA' AND Fecha >= ?";
            Connection conn = ConexionDB.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, caja.fechaApertura);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("Total");
                }
            }
            return 0.0;
        }

        public void cerrarCaja(int idCaja, double montoFinalEfectivo, double totalVentas, String adminUsername) throws SQLException {
            double diferencia = montoFinalEfectivo - totalVentas;
            String sql = "UPDATE Cajas SET FechaCierre = datetime('now','localtime'), " +
                "UsuarioAdminCierre = ?, MontoFinalEfectivo = ?, TotalVentasCalculado = ?, Diferencia = ?, Estado = 'CERRADA' " +
                "WHERE Id = ?";
            Connection conn = ConexionDB.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, adminUsername);
                ps.setDouble(2, montoFinalEfectivo);
                ps.setDouble(3, totalVentas);
                ps.setDouble(4, diferencia);
                ps.setInt(5, idCaja);
                ps.executeUpdate();
            }
        }

        private Caja mapCaja(ResultSet rs) throws SQLException {
            Double montoFinal = rs.getObject("MontoFinalEfectivo") != null ? rs.getDouble("MontoFinalEfectivo") : null;
            Double totalVentas = rs.getObject("TotalVentasCalculado") != null ? rs.getDouble("TotalVentasCalculado") : null;
            Double diferencia = rs.getObject("Diferencia") != null ? rs.getDouble("Diferencia") : null;
            String adminCierre = rs.getObject("UsuarioAdminCierre") != null ? rs.getString("UsuarioAdminCierre") : null;
            return new Caja(rs.getInt("Id"), rs.getString("UsuarioAdminApertura"), rs.getString("FechaApertura"),
                rs.getDouble("MontoInicial"), rs.getString("FechaCierre"),
                adminCierre, montoFinal, totalVentas, diferencia, rs.getString("Estado"));
        }
    }

    static class ProductoDAO {

        public List<Categoria> obtenerCategorias() throws SQLException {
            List<Categoria> list = new ArrayList<>();
            Connection conn = ConexionDB.getInstance().getConnection();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM Categorias")) {
                while (rs.next()) list.add(new Categoria(rs.getInt("IdCategoria"), rs.getString("Nombre")));
            }
            return list;
        }

        public void guardarProducto(Producto p, boolean esNuevo) throws SQLException {
            String sql = esNuevo
                ? "INSERT INTO Productos (CodigoBarras, Nombre, IdCategoria, Costo, PrecioVenta, StockActual, StockMinimo, Activo) VALUES (?,?,?,?,?,?,?,1)"
                : "UPDATE Productos SET CodigoBarras=?, Nombre=?, IdCategoria=?, Costo=?, PrecioVenta=?, StockActual=?, StockMinimo=? WHERE IdProducto=?";
            Connection conn = ConexionDB.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, p.codigoBarras); ps.setString(2, p.nombre); ps.setInt(3, p.idCategoria);
                ps.setDouble(4, p.costo); ps.setDouble(5, p.precio); ps.setInt(6, p.stock); ps.setInt(7, p.stockMinimo);
                if (!esNuevo) ps.setInt(8, p.id);
                ps.executeUpdate();
            }
        }

        public void eliminarProducto(int idProducto) throws SQLException {
            String sql = "UPDATE Productos SET Activo = 0 WHERE IdProducto = ?";
            Connection conn = ConexionDB.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, idProducto); ps.executeUpdate();
            }
        }

        public List<Producto> obtenerTodos() throws SQLException {
            List<Producto> lista = new ArrayList<>();
            String sql = "SELECT p.*, c.Nombre as CatNombre FROM Productos p JOIN Categorias c ON p.IdCategoria = c.IdCategoria WHERE p.Activo=1";
            Connection conn = ConexionDB.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs));
            }
            return lista;
        }

        public Producto buscarPorCodigo(String codigo) throws SQLException {
            String sql = "SELECT p.*, c.Nombre as CatNombre FROM Productos p JOIN Categorias c ON p.IdCategoria = c.IdCategoria WHERE p.Activo=1 AND p.CodigoBarras=?";
            Connection conn = ConexionDB.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, codigo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapRow(rs);
                }
            }
            return null;
        }

        public void actualizarInventario(int id, int cant, String tipo, String user) throws SQLException {
            Connection conn = null;
            try {
                conn = ConexionDB.getInstance().getConnection();
                conn.setAutoCommit(false);

                int stockAnterior = 0;
                try (PreparedStatement ps = conn.prepareStatement("SELECT StockActual FROM Productos WHERE IdProducto = ?")) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) stockAnterior = rs.getInt(1);
                    }
                }
                int stockNuevo = stockAnterior + cant;

                String sqlMov = "INSERT INTO MovimientosInventario (IdProducto, TipoMovimiento, Cantidad, StockAnterior, StockNuevo, UsuarioResponsable) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sqlMov)) {
                    ps.setInt(1, id); ps.setString(2, tipo); ps.setInt(3, cant);
                    ps.setInt(4, stockAnterior); ps.setInt(5, stockNuevo); ps.setString(6, user);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement("UPDATE Productos SET StockActual = StockActual + ? WHERE IdProducto = ?")) {
                    ps.setInt(1, cant); ps.setInt(2, id);
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                if (conn != null) conn.rollback();
                throw e;
            } finally {
                if (conn != null) conn.setAutoCommit(true);
            }
        }

        public Map<String, Object> obtenerEstadisticasHoy() throws SQLException {
            Map<String, Object> stats = new HashMap<>();
            String sqlVentas = "SELECT COALESCE(SUM(Total),0) as TotalVenta, COUNT(*) as Transacciones " +
                    "FROM Ventas WHERE Estado = 'COMPLETADA' AND date(Fecha) = date('now','localtime')";
            String sqlAlertas = "SELECT COUNT(*) FROM Productos WHERE StockActual <= StockMinimo AND Activo = 1";

            Connection conn = ConexionDB.getInstance().getConnection(); // <-- ya NO en try-with-resources

            try (PreparedStatement ps = conn.prepareStatement(sqlVentas);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalVentas", rs.getDouble("TotalVenta"));
                    stats.put("numTransacciones", rs.getInt("Transacciones"));
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(sqlAlertas);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stats.put("stockBajo", rs.getInt(1));
            }

            return stats;
        }

        public List<Producto> obtenerAlertasStock() throws SQLException {
            List<Producto> lista = new ArrayList<>();
            String sql = "SELECT p.*, c.Nombre as CatNombre FROM Productos p " +
                    "JOIN Categorias c ON p.IdCategoria = c.IdCategoria " +
                    "WHERE p.Activo=1 AND p.StockActual <= p.StockMinimo " +
                    "ORDER BY p.StockActual ASC";

            Connection conn = ConexionDB.getInstance().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapRow(rs));
            }
            return lista;
        }

        private Producto mapRow(ResultSet rs) throws SQLException {
            return new Producto(rs.getInt("IdProducto"), rs.getString("CodigoBarras"), rs.getString("Nombre"),
                rs.getString("CatNombre"), rs.getInt("IdCategoria"), rs.getDouble("Costo"),
                rs.getDouble("PrecioVenta"), rs.getInt("StockActual"), rs.getInt("StockMinimo"));
        }

        public void respaldarBaseDatos(String rutaDestino) throws Exception {
            String dbUrl = ConfigService.getDbUrl();
            String dbPath = dbUrl.startsWith("jdbc:sqlite:") ? dbUrl.substring("jdbc:sqlite:".length()) : "data/posabarrotes.db";
            java.io.File origen = new java.io.File(dbPath);
            java.io.File destino = new java.io.File(rutaDestino);
            java.nio.file.Files.copy(origen.toPath(), destino.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ==========================================
    // 8. INTERFAZ GRAFICA (UI)
    // ==========================================
    static class LoginDialog extends JDialog {
        public LoginDialog(JFrame parent) {
            super(parent, "Iniciar Sesion", true);
            setSize(400, 300); setLocationRelativeTo(null); setLayout(new BorderLayout()); setResizable(false);
            JPanel panel = new JPanel(new GridBagLayout()); panel.setBackground(Config.COLOR_BG);
            GridBagConstraints gbc = new GridBagConstraints(); gbc.insets = new Insets(10, 10, 10, 10); gbc.fill = GridBagConstraints.HORIZONTAL;
            JLabel lblTitle = new JLabel("POS SECURITY"); lblTitle.setFont(Config.FONT_BIG); lblTitle.setHorizontalAlignment(SwingConstants.CENTER);
            JTextField txtUser = new JTextField(15); txtUser.setBorder(BorderFactory.createTitledBorder("Usuario"));
            JPasswordField txtPass = new JPasswordField(15); txtPass.setBorder(BorderFactory.createTitledBorder("Contrasena"));
            JButton btnLogin = new JButton("ENTRAR");
            btnLogin.setBackground(Config.COLOR_PRIMARY); btnLogin.setForeground(Color.WHITE);
            btnLogin.setFont(Config.FONT_HEADER); btnLogin.setFocusPainted(false);
            btnLogin.addActionListener(e -> {
                Usuario u = new UsuarioDAO().login(txtUser.getText(), new String(txtPass.getPassword()));
                if (u != null) {
                    AbarrotesPos.sesionActual = u;
                    dispose();
                    try {
                        CajaDAO cajaDAO = new CajaDAO();
                        Caja cajaAbierta = cajaDAO.obtenerCajaAbiertaGlobal();
                        AbarrotesPos.cajaActual = cajaAbierta;
                        if ("ADMIN".equals(u.rol)) {
                            if (cajaAbierta == null) {
                                int resp = JOptionPane.showConfirmDialog(null,
                                    "No hay caja global abierta.\n¿Desea abrir la caja ahora?",
                                    "Apertura de Caja", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                                if (resp == JOptionPane.YES_OPTION) {
                                    String montoStr = JOptionPane.showInputDialog(null,
                                        "Ingrese el monto inicial de la caja:",
                                        "Apertura de Caja", JOptionPane.QUESTION_MESSAGE);
                                    if (montoStr != null && !montoStr.trim().isEmpty()) {
                                        try {
                                            double monto = Double.parseDouble(montoStr.trim());
                                            AbarrotesPos.cajaActual = cajaDAO.abrirCaja(u.username, monto);
                                            JOptionPane.showMessageDialog(null,
                                                "Caja global abierta con $" + String.format("%.2f", monto),
                                                "Caja Abierta", JOptionPane.INFORMATION_MESSAGE);
                                        } catch (NumberFormatException ex) {
                                            JOptionPane.showMessageDialog(null, "Monto inválido. No se abrió la caja.");
                                        }
                                    }
                                }
                            }
                        } else {
                            if (cajaAbierta == null) {
                                JOptionPane.showMessageDialog(null,
                                    "No hay caja global abierta.\nSolicite al administrador que abra la caja antes de comenzar a vender.",
                                    "Caja No Disponible", JOptionPane.WARNING_MESSAGE);
                            }
                        }
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(null, "Error al verificar caja: " + ex.getMessage());
                    }
                    new MainFrame().setVisible(true);
                } else {
                    JOptionPane.showMessageDialog(this, "Credenciales incorrectas.\nPrueba: admin / 1234", "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            gbc.gridx = 0; gbc.gridy = 0; panel.add(lblTitle, gbc);
            gbc.gridy = 1; panel.add(txtUser, gbc);
            gbc.gridy = 2; panel.add(txtPass, gbc);
            gbc.gridy = 3; panel.add(btnLogin, gbc);
            add(panel);
            addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { System.exit(0); } });
        }
    }

    static class SidebarButton extends JButton {
        public SidebarButton(String text) {
            super(text); setHorizontalAlignment(SwingConstants.LEFT); setFocusPainted(false); setBorderPainted(false); setContentAreaFilled(false);
            setForeground(new Color(145, 158, 171)); setFont(Config.FONT_MAIN); setCursor(new Cursor(Cursor.HAND_CURSOR)); setBorder(new EmptyBorder(12, 20, 12, 10));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { setForeground(Color.WHITE); }
                public void mouseExited(MouseEvent e) { setForeground(new Color(145, 158, 171)); }
            });
        }
    }

    static class KPICard extends JPanel {
        public KPICard(String title, String value, Color colorIcon) {
            setLayout(new BorderLayout(15, 0)); setBackground(Config.COLOR_CARD_BG);
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(230,230,230), 1), new EmptyBorder(20, 20, 20, 20)));
            JPanel iconBar = new JPanel(); iconBar.setBackground(colorIcon); iconBar.setPreferredSize(new Dimension(5, 50)); add(iconBar, BorderLayout.WEST);
            JPanel textPanel = new JPanel(new GridLayout(2, 1)); textPanel.setBackground(Config.COLOR_CARD_BG);
            JLabel lblVal = new JLabel(value); lblVal.setFont(Config.FONT_KPI_VALUE); lblVal.setForeground(Color.DARK_GRAY);
            JLabel lblTitle = new JLabel(title.toUpperCase()); lblTitle.setFont(Config.FONT_KPI_TITLE); lblTitle.setForeground(Color.GRAY);
            textPanel.add(lblVal); textPanel.add(lblTitle); add(textPanel, BorderLayout.CENTER);
        }
    }

    static class ProductoDialog extends JDialog {
        public boolean guardado = false;
        public ProductoDialog(Window parent, Producto p, List<Categoria> categorias, ProductoDAO dao) {
            super(parent, p == null ? "Nuevo Producto" : "Editar Producto", Dialog.ModalityType.APPLICATION_MODAL);
            setSize(400, 450); setLocationRelativeTo(parent); setLayout(new GridLayout(9, 2, 10, 15));
            ((JComponent)getContentPane()).setBorder(new EmptyBorder(20, 20, 20, 20));

            JTextField txtCod = new JTextField(p != null ? p.codigoBarras : "");
            JTextField txtNom = new JTextField(p != null ? p.nombre : "");
            JComboBox<Categoria> cbCat = new JComboBox<>(categorias.toArray(new Categoria[0]));
            if (p != null) { for (int i = 0; i < cbCat.getItemCount(); i++) if (cbCat.getItemAt(i).id == p.idCategoria) cbCat.setSelectedIndex(i); }
            JTextField txtCosto = new JTextField(p != null ? String.valueOf(p.costo) : "");
            JTextField txtPrecio = new JTextField(p != null ? String.valueOf(p.precio) : "");
            JTextField txtStock = new JTextField(p != null ? String.valueOf(p.stock) : "");
            JTextField txtStockMin = new JTextField(p != null ? String.valueOf(p.stockMinimo) : "");

            add(new JLabel("Código Barras:")); add(txtCod);
            add(new JLabel("Nombre:")); add(txtNom);
            add(new JLabel("Categoría:")); add(cbCat);
            add(new JLabel("Costo ($):")); add(txtCosto);
            add(new JLabel("Precio Venta ($):")); add(txtPrecio);
            add(new JLabel("Stock Inicial:")); add(txtStock);
            add(new JLabel("Stock Mínimo:")); add(txtStockMin);

            JButton btnGuardar = new JButton("Guardar");
            btnGuardar.setBackground(Config.COLOR_PRIMARY); btnGuardar.setForeground(Color.WHITE);
            JButton btnCancelar = new JButton("Cancelar");
            btnCancelar.setBackground(Config.COLOR_DARK); btnCancelar.setForeground(Color.WHITE);
            btnCancelar.addActionListener(e -> dispose());

            btnGuardar.addActionListener(e -> {
                try {
                    if (txtCod.getText().isEmpty() || txtNom.getText().isEmpty()) { JOptionPane.showMessageDialog(this, "Código y Nombre son obligatorios."); return; }
                    double costo = Double.parseDouble(txtCosto.getText());
                    double precio = Double.parseDouble(txtPrecio.getText());
                    int stock = Integer.parseInt(txtStock.getText());
                    int min = Integer.parseInt(txtStockMin.getText());
                    Categoria c = (Categoria) cbCat.getSelectedItem();
                    Producto nuevo = new Producto(p != null ? p.id : 0, txtCod.getText().trim(), txtNom.getText().trim(), c.nombre, c.id, costo, precio, stock, min);
                    dao.guardarProducto(nuevo, p == null);
                    guardado = true; dispose();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Asegúrese de ingresar números válidos en Costo, Precio y Stock.", "Error", JOptionPane.ERROR_MESSAGE);
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(this, "Error de Base de Datos: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            add(btnGuardar); add(btnCancelar);
        }
    }

    static class MainFrame extends JFrame {
        private JPanel mainContent; private CardLayout cardLayout;
        public MainFrame() {
            setTitle("POS Abarrotes - v8.0 Enterprise Release");
            setSize(1280, 800); setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null); setLayout(new BorderLayout());
            JPanel sidebar = new JPanel(); sidebar.setBackground(Config.COLOR_SIDEBAR);
            sidebar.setPreferredSize(new Dimension(260, getHeight()));
            sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
            JLabel lblLogo = new JLabel("POS ABARROTES"); lblLogo.setForeground(Color.WHITE);
            lblLogo.setFont(new Font("Segoe UI", Font.BOLD, 22)); lblLogo.setBorder(new EmptyBorder(40, 30, 40, 20));
            sidebar.add(lblLogo);
            boolean isAdmin = AbarrotesPos.sesionActual != null && "ADMIN".equals(AbarrotesPos.sesionActual.rol);
            if (isAdmin) addMenu(sidebar, "Dashboard (Inicio)", "DASHBOARD");
            addMenu(sidebar, "Punto de Venta (Alt+V)", "VENTAS");
            addMenu(sidebar, "Historial de Ventas", "HISTORIAL");
            if (isAdmin) addMenu(sidebar, "Gestion de Inventario", "INVENTARIO");
            if (isAdmin) {
                sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
                JPanel separador = new JPanel(); separador.setBackground(new Color(55, 65, 81));
                separador.setMaximumSize(new Dimension(220, 1)); separador.setPreferredSize(new Dimension(220, 1));
                separador.setAlignmentX(LEFT_ALIGNMENT); separador.setBorder(new EmptyBorder(0, 20, 0, 20));
                sidebar.add(separador);
                sidebar.add(Box.createRigidArea(new Dimension(0, 5)));
                SidebarButton btnAbrirCaja = new SidebarButton("Abrir Caja");
                btnAbrirCaja.setMaximumSize(new Dimension(260, 50));
                btnAbrirCaja.addActionListener(e -> abrirCajaAdmin());
                sidebar.add(btnAbrirCaja);
                SidebarButton btnCerrarCaja = new SidebarButton("Cerrar Caja (Corte Z)");
                btnCerrarCaja.setMaximumSize(new Dimension(260, 50));
                btnCerrarCaja.addActionListener(e -> cerrarCajaAdmin());
                sidebar.add(btnCerrarCaja);
            }
            sidebar.add(Box.createVerticalGlue());
            JLabel lblUser = new JLabel("Usuario: " + (sesionActual != null ? sesionActual.username : "N/A"));
            lblUser.setForeground(Color.WHITE); lblUser.setFont(new Font("Segoe UI", Font.BOLD, 14)); lblUser.setBorder(new EmptyBorder(0, 30, 5, 0));
            JLabel lblRol = new JLabel(isAdmin ? "ROL: ADMINISTRADOR" : "ROL: CAJERO");
            lblRol.setForeground(Config.COLOR_SUCCESS); lblRol.setFont(new Font("Segoe UI", Font.PLAIN, 12)); lblRol.setBorder(new EmptyBorder(0, 30, 30, 0));
            sidebar.add(lblUser); sidebar.add(lblRol); add(sidebar, BorderLayout.WEST);
            cardLayout = new CardLayout(); mainContent = new JPanel(cardLayout); mainContent.setBackground(Config.COLOR_BG);
            if (isAdmin) mainContent.add(new DashboardPanel(), "DASHBOARD");
            mainContent.add(new VentasPanel(), "VENTAS");
            mainContent.add(new HistorialPanel(), "HISTORIAL");
            if (isAdmin) mainContent.add(new InventoryPanel(), "INVENTARIO");
            add(mainContent, BorderLayout.CENTER);
            if (isAdmin) cardLayout.show(mainContent, "DASHBOARD"); else cardLayout.show(mainContent, "VENTAS");
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
                if (e.getID() == KeyEvent.KEY_PRESSED && e.isAltDown() && e.getKeyCode() == KeyEvent.VK_V) {
                    cardLayout.show(mainContent, "VENTAS"); return true;
                }
                if (e.getID() == KeyEvent.KEY_PRESSED && e.isAltDown() && e.getKeyCode() == KeyEvent.VK_H) {
                    cardLayout.show(mainContent, "HISTORIAL"); return true;
                }
                return false;
            });
        }
        private void addMenu(JPanel p, String t, String c) {
            SidebarButton b = new SidebarButton(t); b.addActionListener(e -> cardLayout.show(mainContent, c));
            b.setMaximumSize(new Dimension(260, 50)); p.add(b);
        }

        private void abrirCajaAdmin() {
            if (AbarrotesPos.cajaActual != null) {
                JOptionPane.showMessageDialog(this,
                    "Ya hay una caja abierta.\nAbierta el: " + AbarrotesPos.cajaActual.fechaApertura + "\nCiérrela primero para abrir una nueva.",
                    "Caja Ya Abierta", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String montoStr = JOptionPane.showInputDialog(this,
                "Ingrese el monto inicial de la caja:", "Apertura de Caja", JOptionPane.QUESTION_MESSAGE);
            if (montoStr == null) return;
            try {
                double monto = Double.parseDouble(montoStr.trim());
                CajaDAO cajaDAO = new CajaDAO();
                AbarrotesPos.cajaActual = cajaDAO.abrirCaja(AbarrotesPos.sesionActual.username, monto);
                JOptionPane.showMessageDialog(this,
                    "Caja global abierta con $" + String.format("%.2f", monto),
                    "Caja Abierta", JOptionPane.INFORMATION_MESSAGE);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Monto inválido.", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error al abrir caja: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }

        private void cerrarCajaAdmin() {
            if (AbarrotesPos.cajaActual == null) {
                JOptionPane.showMessageDialog(this,
                    "No hay caja abierta actualmente.",
                    "Sin Caja Abierta", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String efectivoStr = JOptionPane.showInputDialog(this,
                "Cierre de Caja Global\nAbierta el: " + AbarrotesPos.cajaActual.fechaApertura +
                "\n\nIngrese el efectivo contado en caja:",
                "Cierre de Caja (Corte Z)", JOptionPane.QUESTION_MESSAGE);
            if (efectivoStr == null) return;
            try {
                double efectivo = Double.parseDouble(efectivoStr.trim());
                CajaDAO cajaDAO = new CajaDAO();
                double totalVentas = cajaDAO.calcularTotalVentasCaja(AbarrotesPos.cajaActual);
                cajaDAO.cerrarCaja(AbarrotesPos.cajaActual.id, efectivo, totalVentas, AbarrotesPos.sesionActual.username);
                Caja cajaCerrada = cajaDAO.obtenerCajaPorId(AbarrotesPos.cajaActual.id);
                AbarrotesPos.cajaActual = null;
                PdfService.generarCorteCajaPDF(cajaCerrada);
                JOptionPane.showMessageDialog(this,
                    "Caja CERRADA exitosamente.\nCorte Z generado en carpeta /Cortes\n\n" +
                    "Ventas del turno: $" + String.format("%.2f", cajaCerrada.totalVentasCalculado) + "\n" +
                    "Efectivo contado: $" + String.format("%.2f", cajaCerrada.montoFinalEfectivo) + "\n" +
                    "Diferencia: $" + String.format("%.2f", cajaCerrada.diferencia),
                    "Corte Exitoso", JOptionPane.INFORMATION_MESSAGE);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Monto inválido.", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error al cerrar caja: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    static class DashboardPanel extends JPanel {
        private ProductoDAO dao; private JPanel kpiPanel; private JTable tableAlertas; private DefaultTableModel modelAlertas;
        public DashboardPanel() {
            setLayout(new BorderLayout(30, 30)); setBackground(Config.COLOR_BG); setBorder(new EmptyBorder(30, 30, 30, 30));
            dao = new ProductoDAO();
            JLabel lblTitle = new JLabel("Resumen de Operaciones");
            lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 24)); lblTitle.setForeground(Config.COLOR_SIDEBAR);
            JButton btnBackup = new JButton("Respaldar BD");
            btnBackup.setFont(Config.FONT_MAIN); btnBackup.setBackground(Config.COLOR_PRIMARY); btnBackup.setForeground(Color.WHITE);
            btnBackup.setFocusPainted(false); btnBackup.addActionListener(e -> ejecutarRespaldoBD());
            JButton btnRefresh = new JButton("Actualizar");
            btnRefresh.setFont(Config.FONT_MAIN); btnRefresh.setBackground(Config.COLOR_INFO); btnRefresh.setForeground(Color.WHITE);
            btnRefresh.addActionListener(e -> cargarDatos());
            JPanel pnlBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT)); pnlBotones.setBackground(Config.COLOR_BG);
            pnlBotones.add(btnBackup); pnlBotones.add(btnRefresh);
            JPanel header = new JPanel(new BorderLayout()); header.setBackground(Config.COLOR_BG);
            header.add(lblTitle, BorderLayout.WEST); header.add(pnlBotones, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);
            JPanel centerPanel = new JPanel(); centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS)); centerPanel.setBackground(Config.COLOR_BG);
            kpiPanel = new JPanel(new GridLayout(1, 3, 20, 0)); kpiPanel.setBackground(Config.COLOR_BG);
            kpiPanel.setPreferredSize(new Dimension(0, 120)); kpiPanel.setMaximumSize(new Dimension(2000, 120));
            centerPanel.add(kpiPanel); centerPanel.add(Box.createRigidArea(new Dimension(0, 30)));
            centerPanel.add(new JLabel("Alertas de Stock"));
            modelAlertas = new DefaultTableModel(new String[]{"ID", "Producto", "Categoría", "Stock", "Mínimo"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            tableAlertas = new JTable(modelAlertas); tableAlertas.setRowHeight(30);
            tableAlertas.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
                public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                    Component cp = super.getTableCellRendererComponent(t, v, s, f, r, c);
                    if (!s) { cp.setBackground(new Color(255, 235, 238)); cp.setForeground(Config.COLOR_DANGER); }
                    return cp;
                }
            });
            centerPanel.add(new JScrollPane(tableAlertas));
            add(centerPanel, BorderLayout.CENTER);
            cargarDatos();
        }

        private void cargarDatos() {
            new SwingWorker<Map<String, Object>, Void>() {
                List<Producto> alertas;
                protected Map<String, Object> doInBackground() throws Exception {
                    alertas = dao.obtenerAlertasStock(); return dao.obtenerEstadisticasHoy();
                }
                protected void done() {
                    try {
                        Map<String, Object> stats = get(); kpiPanel.removeAll();
                        kpiPanel.add(new KPICard("Ventas", String.format("$%.2f", stats.get("totalVentas") != null ? stats.get("totalVentas") : 0.0), Config.COLOR_SUCCESS));
                        kpiPanel.add(new KPICard("Transacciones", "" + (stats.get("numTransacciones") != null ? stats.get("numTransacciones") : 0), Config.COLOR_INFO));
                        kpiPanel.add(new KPICard("Alertas", "" + (stats.get("stockBajo") != null ? stats.get("stockBajo") : 0), Config.COLOR_DANGER));
                        kpiPanel.revalidate(); modelAlertas.setRowCount(0);
                        for (Producto p : alertas) modelAlertas.addRow(new Object[]{p.id, p.nombre, p.categoria, p.stock, p.stockMinimo});
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }.execute();
        }

        private void ejecutarRespaldoBD() {
            JFileChooser chooser = new JFileChooser(); chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Seleccione carpeta para guardar el respaldo");
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String rutaFinal = chooser.getSelectedFile().getAbsolutePath() + File.separator + "PosAbarrotes_Backup_" + timestamp + ".db";
                new SwingWorker<Void, Void>() {
                    Exception error = null;
                    protected Void doInBackground() { try { dao.respaldarBaseDatos(rutaFinal); } catch (Exception e) { error = e; } return null; }
                    protected void done() {
                        if (error == null) {
                            JOptionPane.showMessageDialog(DashboardPanel.this, "Respaldo creado exitosamente!\nRuta: " + rutaFinal, "Respaldo Exitoso", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(DashboardPanel.this, "Error al respaldar.\nDetalle: " + error.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }.execute();
            }
        }
    }

    static class VentasPanel extends JPanel {
        private DefaultTableModel modelTicket;
        private JTextField txtCodigo; private JTextField txtCantidad;
        private JLabel lblTotal; private JTable table;
        private List<DetalleVenta> carrito; private ProductoDAO productoDAO;

        public VentasPanel() {
            setLayout(new BorderLayout(15, 15)); setBackground(Config.COLOR_BG); setBorder(new EmptyBorder(20, 20, 20, 20));
            productoDAO = new ProductoDAO(); carrito = new ArrayList<>();
            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT)); topPanel.setBackground(Config.COLOR_BG);
            txtCodigo = new JTextField(15); txtCodigo.setFont(Config.FONT_BIG); txtCodigo.addActionListener(e -> agregarProducto());
            txtCantidad = new JTextField("1", 3); txtCantidad.setFont(Config.FONT_BIG);
            txtCantidad.setHorizontalAlignment(JTextField.CENTER); txtCantidad.addActionListener(e -> agregarProducto());
            JButton btnBuscar = new JButton("Agregar");
            btnBuscar.setBackground(Config.COLOR_SUCCESS); btnBuscar.setForeground(Color.WHITE);
            btnBuscar.addActionListener(e -> agregarProducto());
            topPanel.add(new JLabel("Producto:")); topPanel.add(txtCodigo);
            topPanel.add(Box.createRigidArea(new Dimension(15, 0)));
            topPanel.add(new JLabel("Cant:")); topPanel.add(txtCantidad);
            topPanel.add(Box.createRigidArea(new Dimension(15, 0))); topPanel.add(btnBuscar);
            add(topPanel, BorderLayout.NORTH);

            modelTicket = new DefaultTableModel(new String[]{"Código", "Producto", "Precio", "Cant.", "Subtotal"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            table = new JTable(modelTicket); table.setRowHeight(30);
            table.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) editarCantidad(); } });
            add(new JScrollPane(table), BorderLayout.CENTER);

            JPanel rightPanel = new JPanel(); rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
            rightPanel.setBackground(Color.WHITE); rightPanel.setPreferredSize(new Dimension(300, 0));
            lblTotal = new JLabel("$0.00"); lblTotal.setFont(Config.FONT_HUGE); lblTotal.setAlignmentX(CENTER_ALIGNMENT);
            JButton btnCobrar = new JButton("COBRAR");
            btnCobrar.setBackground(Config.COLOR_SUCCESS); btnCobrar.setForeground(Color.WHITE);
            btnCobrar.setMaximumSize(new Dimension(280, 50)); btnCobrar.setAlignmentX(CENTER_ALIGNMENT);
            btnCobrar.addActionListener(e -> procesarCobro());
            JButton btnEditar = new JButton("Editar Cantidad");
            btnEditar.setBackground(Config.COLOR_PRIMARY); btnEditar.setForeground(Color.WHITE);
            btnEditar.setMaximumSize(new Dimension(280, 40)); btnEditar.setAlignmentX(CENTER_ALIGNMENT);
            btnEditar.addActionListener(e -> editarCantidad());
            JButton btnEliminar = new JButton("Quitar (Del)");
            btnEliminar.setBackground(Config.COLOR_WARNING); btnEliminar.setForeground(Color.WHITE);
            btnEliminar.setMaximumSize(new Dimension(280, 40)); btnEliminar.setAlignmentX(CENTER_ALIGNMENT);
            btnEliminar.addActionListener(e -> eliminarProducto());
            JButton btnCancelar = new JButton("Cancelar Venta");
            btnCancelar.setBackground(Config.COLOR_DANGER); btnCancelar.setForeground(Color.WHITE);
            btnCancelar.setMaximumSize(new Dimension(280, 40)); btnCancelar.setAlignmentX(CENTER_ALIGNMENT);
            btnCancelar.addActionListener(e -> cancelarVenta());
            JButton btnCorte = new JButton("Corte Caja");
            btnCorte.setBackground(Config.COLOR_DARK); btnCorte.setForeground(Color.WHITE);
            btnCorte.setMaximumSize(new Dimension(280, 40)); btnCorte.setAlignmentX(CENTER_ALIGNMENT);
            btnCorte.addActionListener(e -> generarCorteCaja());

            rightPanel.add(Box.createVerticalGlue()); rightPanel.add(lblTotal);
            rightPanel.add(Box.createRigidArea(new Dimension(0, 20))); rightPanel.add(btnCobrar);
            rightPanel.add(Box.createRigidArea(new Dimension(0, 20))); rightPanel.add(btnEditar);
            rightPanel.add(Box.createRigidArea(new Dimension(0, 10))); rightPanel.add(btnEliminar);
            rightPanel.add(Box.createRigidArea(new Dimension(0, 10))); rightPanel.add(btnCancelar);
            rightPanel.add(Box.createRigidArea(new Dimension(0, 20))); rightPanel.add(btnCorte);
            rightPanel.add(Box.createVerticalGlue());
            add(rightPanel, BorderLayout.EAST);

            InputMap im = table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            ActionMap am = table.getActionMap();
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "edt");
            am.put("edt", new AbstractAction() { public void actionPerformed(ActionEvent e) { editarCantidad(); } });
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "del");
            am.put("del", new AbstractAction() { public void actionPerformed(ActionEvent e) { eliminarProducto(); } });
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "esc");
            am.put("esc", new AbstractAction() { public void actionPerformed(ActionEvent e) { cancelarVenta(); } });
        }

        private void agregarProducto() {
            String codigo = txtCodigo.getText().trim(); if (codigo.isEmpty()) return;
            int cantSolicitada;
            try { cantSolicitada = Integer.parseInt(txtCantidad.getText().trim()); if (cantSolicitada <= 0) throw new Exception(); }
            catch (Exception ex) { JOptionPane.showMessageDialog(this, "Cantidad inicial inválida"); return; }
            final int cFinal = cantSolicitada;
            new SwingWorker<Producto, Void>() {
                protected Producto doInBackground() throws Exception { return productoDAO.buscarPorCodigo(codigo); }
                protected void done() {
                    try {
                        Producto p = get();
                        if (p != null) {
                            if (p.stock < cFinal) { JOptionPane.showMessageDialog(VentasPanel.this, "Stock insuficiente. Disponible: " + p.stock); return; }
                            boolean existe = false;
                            for (DetalleVenta d : carrito) {
                                if (d.producto.id == p.id) {
                                    if (d.cantidad + cFinal > p.stock) { JOptionPane.showMessageDialog(VentasPanel.this, "Stock insuficiente. Disponible total: " + p.stock); return; }
                                    d.setCantidad(d.cantidad + cFinal); existe = true; break;
                                }
                            }
                            if (!existe) carrito.add(new DetalleVenta(p, cFinal));
                            actualizarTabla(); txtCodigo.setText(""); txtCantidad.setText("1"); txtCodigo.requestFocus();
                        } else JOptionPane.showMessageDialog(VentasPanel.this, "No encontrado");
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }.execute();
        }

        private void editarCantidad() {
            int r = table.getSelectedRow(); if (r == -1) { JOptionPane.showMessageDialog(this, "Seleccione un producto."); return; }
            DetalleVenta d = carrito.get(r);
            String in = JOptionPane.showInputDialog(this, "Stock: " + d.producto.stock + "\nNueva Cantidad:", d.cantidad);
            if (in != null) {
                try {
                    int c = Integer.parseInt(in);
                    if (c > 0 && c <= d.producto.stock) { d.setCantidad(c); actualizarTabla(); }
                    else JOptionPane.showMessageDialog(this, "Stock insuficiente");
                } catch (Exception e) { JOptionPane.showMessageDialog(this, "Número inválido"); }
            }
        }

        private void eliminarProducto() {
            int r = table.getSelectedRow();
            if (r != -1 && JOptionPane.showConfirmDialog(this, "Eliminar?", "Conf", JOptionPane.YES_NO_OPTION) == 0) {
                carrito.remove(r); actualizarTabla();
            }
        }

        private void cancelarVenta() {
            if (!carrito.isEmpty() && JOptionPane.showConfirmDialog(this, "Cancelar venta?", "Conf", JOptionPane.YES_NO_OPTION) == 0) {
                carrito.clear(); actualizarTabla(); txtCodigo.requestFocus();
            }
        }

        private void actualizarTabla() {
            modelTicket.setRowCount(0); double t = 0;
            for (DetalleVenta d : carrito) {
                modelTicket.addRow(new Object[]{d.producto.codigoBarras, d.producto.nombre, d.producto.precio, d.cantidad, d.subtotal});
                t += d.subtotal;
            }
            lblTotal.setText(String.format("$%.2f", t));
        }

        private void procesarCobro() {
            if (AbarrotesPos.cajaActual == null) {
                String msg = "No hay caja global abierta.\n" +
                    ("ADMIN".equals(AbarrotesPos.sesionActual != null ? AbarrotesPos.sesionActual.rol : "")
                        ? "Use 'Abrir Caja' en el menú lateral para iniciar operaciones."
                        : "Solicite al administrador que abra la caja antes de cobrar.");
                JOptionPane.showMessageDialog(this, msg, "Caja Cerrada", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (carrito.isEmpty()) return;
            double tot = carrito.stream().mapToDouble(d -> d.subtotal).sum();
            String pStr = JOptionPane.showInputDialog("Total: $" + String.format("%.2f", tot) + "\nPago:");
            if (pStr != null) {
                try {
                    double pay = Double.parseDouble(pStr);
                    if (pay >= tot) {
                        List<DetalleVenta> carritoSnapshot = new ArrayList<>(carrito);
                        String cajero = AbarrotesPos.sesionActual.username;
                        VentaDAO ventaDAO = new VentaDAO();
                        Venta venta = ventaDAO.registrarVenta(carritoSnapshot, tot, cajero);
                        for (DetalleVenta d : carritoSnapshot)
                            productoDAO.actualizarInventario(d.producto.id, -d.cantidad, "VENTA", cajero);
                        PdfService.generarTicketPDF(carritoSnapshot, tot, pay, pay - tot, venta.folio);
                        JOptionPane.showMessageDialog(this,
                            "Venta registrada!\nFolio: " + venta.folio + "\nCambio: $" + String.format("%.2f", pay - tot),
                            "Venta Exitosa", JOptionPane.INFORMATION_MESSAGE);
                        carrito.clear(); actualizarTabla(); txtCantidad.setText("1"); txtCodigo.requestFocus();
                    } else JOptionPane.showMessageDialog(this, "Pago insuficiente");
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Error al procesar la venta: " + e.getMessage());

                }
            }
        }

        private void generarCorteCaja() {
            if (AbarrotesPos.sesionActual == null || !"ADMIN".equals(AbarrotesPos.sesionActual.rol)) {
                JOptionPane.showMessageDialog(this,
                    "Solo el administrador puede cerrar la caja.\nSolicite al administrador que use 'Cerrar Caja (Corte Z)' en el menú lateral.",
                    "Acceso Restringido", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (AbarrotesPos.cajaActual == null) {
                JOptionPane.showMessageDialog(this, "No hay caja abierta actualmente.", "Sin Caja Abierta", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                String efectivoStr = JOptionPane.showInputDialog(this,
                    "Cierre de Caja Global\nAbierta el: " + AbarrotesPos.cajaActual.fechaApertura +
                    "\n\nIngrese el efectivo contado en caja:",
                    "Cierre de Caja (Corte Z)", JOptionPane.QUESTION_MESSAGE);
                if (efectivoStr == null) return;
                double efectivo;
                try { efectivo = Double.parseDouble(efectivoStr.trim()); }
                catch (NumberFormatException ex) { JOptionPane.showMessageDialog(this, "Monto inválido."); return; }

                CajaDAO cajaDAO = new CajaDAO();
                double totalVentas = cajaDAO.calcularTotalVentasCaja(AbarrotesPos.cajaActual);
                cajaDAO.cerrarCaja(AbarrotesPos.cajaActual.id, efectivo, totalVentas, AbarrotesPos.sesionActual.username);

                Caja cajaCerrada = cajaDAO.obtenerCajaPorId(AbarrotesPos.cajaActual.id);
                AbarrotesPos.cajaActual = null;

                PdfService.generarCorteCajaPDF(cajaCerrada);
                JOptionPane.showMessageDialog(this,
                    "Caja CERRADA exitosamente.\nCorte Z generado en carpeta /Cortes\n\n" +
                    "Ventas del turno: $" + String.format("%.2f", cajaCerrada.totalVentasCalculado) + "\n" +
                    "Efectivo contado: $" + String.format("%.2f", cajaCerrada.montoFinalEfectivo) + "\n" +
                    "Diferencia: $" + String.format("%.2f", cajaCerrada.diferencia),
                    "Corte Exitoso", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Error al generar corte: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    static class InventoryPanel extends JPanel {
        private DefaultTableModel model; private ProductoDAO dao = new ProductoDAO(); private JTable table; private List<Producto> listaActual;
        public InventoryPanel() {
            setLayout(new BorderLayout(15, 15)); setBackground(Config.COLOR_BG); setBorder(new EmptyBorder(20, 20, 20, 20));

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); top.setBackground(Config.COLOR_BG);
            JButton btnNuevo = new JButton("Nuevo Producto");
            btnNuevo.setBackground(Config.COLOR_SUCCESS); btnNuevo.setForeground(Color.WHITE);
            btnNuevo.addActionListener(e -> gestionarProducto(null));
            JButton btnEditar = new JButton("Editar Selección");
            btnEditar.setBackground(Config.COLOR_PRIMARY); btnEditar.setForeground(Color.WHITE);
            btnEditar.addActionListener(e -> editarProducto());
            JButton btnEliminar = new JButton("Eliminar");
            btnEliminar.setBackground(Config.COLOR_DANGER); btnEliminar.setForeground(Color.WHITE);
            btnEliminar.addActionListener(e -> eliminarProducto());
            JButton btnRecargar = new JButton("Recargar");
            btnRecargar.setBackground(Config.COLOR_INFO); btnRecargar.setForeground(Color.WHITE);
            btnRecargar.addActionListener(e -> cargar());

            top.add(btnNuevo); top.add(btnEditar); top.add(btnEliminar); top.add(btnRecargar);
            add(top, BorderLayout.NORTH);

            model = new DefaultTableModel(new String[]{"ID","Código","Producto","Categoría","Precio","Stock"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            table = new JTable(model); table.setRowHeight(30); table.setFont(Config.FONT_MAIN);
            table.getTableHeader().setFont(Config.FONT_HEADER);
            add(new JScrollPane(table), BorderLayout.CENTER);
            cargar();
        }

        private void cargar() {
            new SwingWorker<List<Producto>, Void>() {
                protected List<Producto> doInBackground() throws Exception { return dao.obtenerTodos(); }
                protected void done() {
                    try { listaActual = get(); model.setRowCount(0); for (Producto p : listaActual) model.addRow(p.toRow()); }
                    catch (Exception e) { e.printStackTrace(); }
                }
            }.execute();
        }

        private void gestionarProducto(Producto p) {
            new SwingWorker<List<Categoria>, Void>() {
                protected List<Categoria> doInBackground() throws Exception { return dao.obtenerCategorias(); }
                protected void done() {
                    try {
                        List<Categoria> cats = get();
                        if (cats.isEmpty()) { JOptionPane.showMessageDialog(InventoryPanel.this, "Debe existir al menos 1 categoría en la BD."); return; }
                        ProductoDialog dlg = new ProductoDialog(SwingUtilities.getWindowAncestor(InventoryPanel.this), p, cats, dao);
                        dlg.setVisible(true);
                        if (dlg.guardado) cargar();
                    } catch (Exception e) { JOptionPane.showMessageDialog(InventoryPanel.this, "Error al cargar categorías: " + e.getMessage()); }
                }
            }.execute();
        }

        private void editarProducto() {
            int r = table.getSelectedRow();
            if (r == -1) { JOptionPane.showMessageDialog(this, "Seleccione un producto para editar."); return; }
            gestionarProducto(listaActual.get(r));
        }

        private void eliminarProducto() {
            int r = table.getSelectedRow();
            if (r == -1) { JOptionPane.showMessageDialog(this, "Seleccione un producto para eliminar."); return; }
            Producto p = listaActual.get(r);
            if (JOptionPane.showConfirmDialog(this, "¿Está seguro de eliminar el producto: " + p.nombre + "?\n\n(Se desactivará para no afectar el historial de ventas)", "Confirmar Eliminación", JOptionPane.YES_NO_OPTION) == 0) {
                new SwingWorker<Void, Void>() {
                    Exception error;
                    protected Void doInBackground() { try { dao.eliminarProducto(p.id); } catch (Exception e) { error = e; } return null; }
                    protected void done() { if (error != null) JOptionPane.showMessageDialog(InventoryPanel.this, "Error: " + error.getMessage()); else cargar(); }
                }.execute();
            }
        }
    }

    // ==========================================
    // HISTORIAL PANEL
    // ==========================================
    static class HistorialPanel extends JPanel {
        private DefaultTableModel modelVentas;
        private JTable tableVentas;
        private List<Venta> listaVentas = new ArrayList<>();
        private VentaDAO ventaDAO = new VentaDAO();

        public HistorialPanel() {
            setLayout(new BorderLayout(10, 10));
            setBackground(Config.COLOR_BG);
            setBorder(new EmptyBorder(20, 20, 20, 20));

            JLabel lblTitle = new JLabel("Historial de Ventas");
            lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 24));
            lblTitle.setForeground(Config.COLOR_SIDEBAR);

            JButton btnHoy = new JButton("Hoy");
            btnHoy.setBackground(Config.COLOR_PRIMARY); btnHoy.setForeground(Color.WHITE);
            btnHoy.setFocusPainted(false);
            btnHoy.addActionListener(e -> cargarVentas(false));

            JButton btn7Dias = new JButton("Últimos 7 días");
            btn7Dias.setBackground(Config.COLOR_INFO); btn7Dias.setForeground(Color.WHITE);
            btn7Dias.setFocusPainted(false);
            btn7Dias.addActionListener(e -> cargarVentas(true));

            JButton btnDetalle = new JButton("Ver Detalle");
            btnDetalle.setBackground(Config.COLOR_SUCCESS); btnDetalle.setForeground(Color.WHITE);
            btnDetalle.setFocusPainted(false);
            btnDetalle.addActionListener(e -> verDetalle());

            JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            topRight.setBackground(Config.COLOR_BG);
            topRight.add(btnHoy); topRight.add(btn7Dias); topRight.add(btnDetalle);

            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(Config.COLOR_BG);
            header.add(lblTitle, BorderLayout.WEST);
            header.add(topRight, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            boolean isAdmin = AbarrotesPos.sesionActual != null && "ADMIN".equals(AbarrotesPos.sesionActual.rol);
            String[] columnas = isAdmin
                ? new String[]{"Folio", "Fecha", "Cajero", "Total", "Estado"}
                : new String[]{"Folio", "Fecha", "Total", "Estado"};
            modelVentas = new DefaultTableModel(columnas, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            tableVentas = new JTable(modelVentas);
            tableVentas.setRowHeight(28);
            tableVentas.setFont(Config.FONT_MAIN);
            tableVentas.getTableHeader().setFont(Config.FONT_HEADER);
            tableVentas.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            tableVentas.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) verDetalle(); }
            });
            add(new JScrollPane(tableVentas), BorderLayout.CENTER);

            JLabel lblAyuda = new JLabel("Doble clic o botón 'Ver Detalle' para ver la venta seleccionada  |  Alt+H: Historial");
            lblAyuda.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            lblAyuda.setForeground(Color.GRAY);
            add(lblAyuda, BorderLayout.SOUTH);

            cargarVentas(false);
        }

        private void cargarVentas(boolean ultimos7Dias) {
            String usuario = AbarrotesPos.sesionActual != null ? AbarrotesPos.sesionActual.username : "";
            String rolKey = AbarrotesPos.sesionActual != null ? AbarrotesPos.sesionActual.rol : "";
            boolean isAdmin = "ADMIN".equals(rolKey);
            new SwingWorker<List<Venta>, Void>() {
                protected List<Venta> doInBackground() throws Exception {
                    return ultimos7Dias
                        ? ventaDAO.obtenerVentasUltimos7Dias(rolKey)
                        : ventaDAO.obtenerVentasHoy(rolKey);
                }
                protected void done() {
                    try {
                        listaVentas = get();
                        modelVentas.setRowCount(0);
                        for (Venta v : listaVentas) {
                            if (isAdmin)
                                modelVentas.addRow(new Object[]{v.folio, v.fecha, v.usuarioCajero, String.format("$%.2f", v.total), v.estado});
                            else
                                modelVentas.addRow(new Object[]{v.folio, v.fecha, String.format("$%.2f", v.total), v.estado});
                        }
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(HistorialPanel.this, "Error al cargar ventas: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }.execute();
        }

        private void verDetalle() {
            int r = tableVentas.getSelectedRow();
            if (r == -1) { JOptionPane.showMessageDialog(this, "Seleccione una venta para ver el detalle."); return; }
            Venta venta = listaVentas.get(r);
            new SwingWorker<List<DetalleVentaGuardado>, Void>() {
                protected List<DetalleVentaGuardado> doInBackground() throws Exception {
                    return ventaDAO.obtenerDetalleVenta(venta.id);
                }
                protected void done() {
                    try {
                        List<DetalleVentaGuardado> detalles = get();
                        mostrarDialogoDetalle(venta, detalles);
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(HistorialPanel.this, "Error al cargar detalle: " + e.getMessage());
                    }
                }
            }.execute();
        }

        private void mostrarDialogoDetalle(Venta venta, List<DetalleVentaGuardado> detalles) {
            JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Detalle de Venta", Dialog.ModalityType.APPLICATION_MODAL);
            dlg.setSize(550, 480); dlg.setLocationRelativeTo(this);
            dlg.setLayout(new BorderLayout(10, 10));
            ((JComponent) dlg.getContentPane()).setBorder(new EmptyBorder(15, 15, 15, 15));

            JPanel infoPanel = new JPanel(new GridLayout(4, 2, 5, 5));
            infoPanel.setBackground(Config.COLOR_CARD_BG);
            infoPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(230, 230, 230)),
                new EmptyBorder(10, 10, 10, 10)));
            addInfoRow(infoPanel, "Folio:", venta.folio);
            addInfoRow(infoPanel, "Fecha:", venta.fecha);
            addInfoRow(infoPanel, "Cajero:", venta.usuarioCajero);
            addInfoRow(infoPanel, "Total:", String.format("$%.2f", venta.total));
            dlg.add(infoPanel, BorderLayout.NORTH);

            DefaultTableModel modelDet = new DefaultTableModel(new String[]{"Producto", "Cant.", "Precio Unit.", "Subtotal"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            for (DetalleVentaGuardado d : detalles)
                modelDet.addRow(new Object[]{d.productoNombre, d.cantidad,
                    String.format("$%.2f", d.precioUnitario), String.format("$%.2f", d.subtotal)});
            JTable tableDet = new JTable(modelDet); tableDet.setRowHeight(26);
            dlg.add(new JScrollPane(tableDet), BorderLayout.CENTER);

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
            btnPanel.setBackground(Config.COLOR_BG);

            JButton btnCopiarFolio = new JButton("Copiar Folio");
            btnCopiarFolio.setBackground(Config.COLOR_PRIMARY); btnCopiarFolio.setForeground(Color.WHITE);
            btnCopiarFolio.setFocusPainted(false);
            btnCopiarFolio.addActionListener(e -> {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(venta.folio), null);
                JOptionPane.showMessageDialog(dlg, "Folio copiado: " + venta.folio, "Folio Copiado", JOptionPane.INFORMATION_MESSAGE);
            });

            JButton btnTicket = new JButton("Generar Ticket PDF");
            btnTicket.setBackground(Config.COLOR_DANGER); btnTicket.setForeground(Color.WHITE);
            btnTicket.setFocusPainted(false);
            btnTicket.addActionListener(e -> {
                List<DetalleVenta> dvList = new ArrayList<>();
                for (DetalleVentaGuardado d : detalles) {
                    Producto p = new Producto(d.productoId, "", d.productoNombre, "", 0, 0, d.precioUnitario, d.cantidad, 0);
                    DetalleVenta dv = new DetalleVenta(p, d.cantidad);
                    dvList.add(dv);
                }
                PdfService.generarTicketVentaPDF(venta, dvList);
            });

            JButton btnCerrar = new JButton("Cerrar");
            btnCerrar.setBackground(Config.COLOR_DARK); btnCerrar.setForeground(Color.WHITE);
            btnCerrar.setFocusPainted(false);
            btnCerrar.addActionListener(e -> dlg.dispose());

            btnPanel.add(btnCopiarFolio); btnPanel.add(btnTicket); btnPanel.add(btnCerrar);
            dlg.add(btnPanel, BorderLayout.SOUTH);
            dlg.setVisible(true);
        }

        private void addInfoRow(JPanel panel, String label, String value) {
            JLabel lbl = new JLabel(label); lbl.setFont(Config.FONT_MAIN.deriveFont(Font.BOLD));
            JLabel val = new JLabel(value); val.setFont(Config.FONT_MAIN);
            panel.add(lbl); panel.add(val);
        }
    }
}
