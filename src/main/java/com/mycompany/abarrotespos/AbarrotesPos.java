package com.mycompany.abarrotespos;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.HashMap;

// IMPORTACIONES PARA PDF (iText)
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

/**
 * PROYECTO: POS ABARROTES INTEGRAL
 * ACTUALIZACIÓN: Módulo Administrador CRUD de Productos (Alta, Baja, Modificación)
 * ARQUITECTURA: MVC Simplificado (Llave en Mano)
 */
public class AbarrotesPos {

    public static Usuario sesionActual;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.put("Button.arc", 12);
            UIManager.put("Component.arc", 12);
            UIManager.put("TextComponent.arc", 12);
            UIManager.put("ProgressBar.arc", 12);
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            if (UsuarioDAO.inicializarTablaUsuarios()) {
                new LoginDialog(null).setVisible(true);
            } else {
                JOptionPane.showMessageDialog(null, "Error crítico conectando a BD.");
            }
        });
    }

    // ==========================================
    // 1. CONFIGURACIÓN
    // ==========================================
    static class Config {
        static final String DB_URL = "jdbc:sqlserver://localhost:1433;databaseName=PosAbarrotes;encrypt=true;trustServerCertificate=true;";
        static final String DB_USER = "sa";
        static final String DB_PASS = "12345";

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
    // 2. SERVICIOS (BD & PDF)
    // ==========================================
    public static class ConexionDB {
        private static ConexionDB instance;
        private Connection connection;
        private ConexionDB() {}
        public static synchronized ConexionDB getInstance() { if (instance == null) instance = new ConexionDB(); return instance; }
        public Connection getConnection() throws SQLException {
            if (connection == null || connection.isClosed()) {
                try { Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver"); connection = DriverManager.getConnection(Config.DB_URL, Config.DB_USER, Config.DB_PASS); }
                catch (ClassNotFoundException e) { throw new SQLException("Driver JDBC no encontrado.", e); }
            }
            return connection;
        }
    }

    static class PdfService {
        public static void generarTicketPDF(List<DetalleVenta> carrito, double total, double pago, double cambio) {
            String nombreArchivo = "Tickets/Ticket_" + System.currentTimeMillis() + ".pdf";
            crearDirectorio("Tickets");
            try {
                PdfWriter writer = new PdfWriter(nombreArchivo); PdfDocument pdf = new PdfDocument(writer); Document document = new Document(pdf);
                document.add(new Paragraph("TIENDA ABARROTES").setTextAlignment(TextAlignment.CENTER).setBold().setFontSize(18));
                document.add(new Paragraph("Ticket de Venta").setTextAlignment(TextAlignment.CENTER).setFontSize(12));
                document.add(new Paragraph("Fecha: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).setTextAlignment(TextAlignment.CENTER));
                document.add(new Paragraph("------------------------------------------------").setTextAlignment(TextAlignment.CENTER));
                Table table = new Table(UnitValue.createPercentArray(new float[]{4, 1, 2})); table.setWidth(UnitValue.createPercentValue(100));
                table.addHeaderCell("Producto"); table.addHeaderCell("Cant"); table.addHeaderCell("Total");
                for (DetalleVenta d : carrito) { table.addCell(d.producto.nombre); table.addCell(String.valueOf(d.cantidad)); table.addCell(String.format("$%.2f", d.subtotal)); }
                document.add(table);
                document.add(new Paragraph("------------------------------------------------").setTextAlignment(TextAlignment.CENTER));
                document.add(new Paragraph(String.format("TOTAL: $%.2f", total)).setTextAlignment(TextAlignment.RIGHT).setBold().setFontSize(14));
                document.add(new Paragraph(String.format("Efectivo: $%.2f", pago)).setTextAlignment(TextAlignment.RIGHT));
                document.add(new Paragraph(String.format("Cambio: $%.2f", cambio)).setTextAlignment(TextAlignment.RIGHT));
                document.add(new Paragraph("\n¡Gracias por su compra!").setTextAlignment(TextAlignment.CENTER));
                document.close();
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(new File(nombreArchivo));
            } catch (Exception e) { JOptionPane.showMessageDialog(null, "Error generando PDF: " + e.getMessage()); e.printStackTrace(); }
        }

        public static void generarCortePDF(Map<String, Object> stats) {
            String nombreArchivo = "Cortes/Corte_" + LocalDate.now() + ".pdf"; crearDirectorio("Cortes");
            try {
                PdfWriter writer = new PdfWriter(nombreArchivo); PdfDocument pdf = new PdfDocument(writer); Document document = new Document(pdf);
                document.add(new Paragraph("CORTE DE CAJA (Z)").setTextAlignment(TextAlignment.CENTER).setBold().setFontSize(20));
                document.add(new Paragraph("Fecha: " + LocalDate.now()).setTextAlignment(TextAlignment.CENTER)); document.add(new Paragraph("\n"));
                double ventas = (double) stats.getOrDefault("totalVentas", 0.0); int trans = (int) stats.getOrDefault("numTransacciones", 0);
                document.add(new Paragraph("Resumen del Día:").setBold());
                document.add(new Paragraph("Ventas Totales: $" + String.format("%.2f", ventas)));
                document.add(new Paragraph("Transacciones: " + trans)); document.add(new Paragraph("\n\n"));
                document.add(new Paragraph("__________________________").setTextAlignment(TextAlignment.CENTER));
                document.add(new Paragraph("Firma del Cajero").setTextAlignment(TextAlignment.CENTER));
                document.close();
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(new File(nombreArchivo));
            } catch (Exception e) { e.printStackTrace(); }
        }
        private static void crearDirectorio(String ruta) { File directorio = new File(ruta); if (!directorio.exists()) directorio.mkdirs(); }
    }

    // ==========================================
    // 3. MODELO Y DAO
    // ==========================================
    static class Usuario {
        int id; String username; String rol;
        public Usuario(int id, String u, String r) { this.id = id; this.username = u; this.rol = r; }
    }

    // NUEVA ENTIDAD: Categoría
    static class Categoria {
        int id; String nombre;
        public Categoria(int id, String nombre) { this.id = id; this.nombre = nombre; }
        @Override public String toString() { return nombre; } // Necesario para el JComboBox
    }

    static class UsuarioDAO {
        public static boolean inicializarTablaUsuarios() {
            String sqlCheck = "IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Usuarios' and xtype='U') BEGIN CREATE TABLE Usuarios (Id INT IDENTITY(1,1) PRIMARY KEY, Username NVARCHAR(50) UNIQUE, Password NVARCHAR(50), Rol NVARCHAR(20)); INSERT INTO Usuarios (Username, Password, Rol) VALUES ('admin', '1234', 'ADMIN'); INSERT INTO Usuarios (Username, Password, Rol) VALUES ('cajero', '1234', 'CAJERO'); END";
            try (Connection conn = ConexionDB.getInstance().getConnection(); Statement stmt = conn.createStatement()) { stmt.execute(sqlCheck); return true; } catch (SQLException e) { return false; }
        }
        public Usuario login(String user, String pass) {
            String sql = "SELECT * FROM Usuarios WHERE Username = ? AND Password = ?";
            try (Connection conn = ConexionDB.getInstance().getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, user); ps.setString(2, pass);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return new Usuario(rs.getInt("Id"), rs.getString("Username"), rs.getString("Rol")); }
            } catch (SQLException e) { e.printStackTrace(); } return null;
        }
    }

    static class Producto {
        int id, idCategoria, stock, stockMinimo; String codigoBarras, nombre, categoria; double costo, precio;
        public Producto(int id, String codigo, String nom, String cat, int idCat, double costo, double precio, int stock, int min) {
            this.id = id; this.codigoBarras = codigo; this.nombre = nom; this.categoria = cat; this.idCategoria = idCat; this.costo = costo; this.precio = precio; this.stock = stock; this.stockMinimo = min;
        }
        public Object[] toRow() { return new Object[]{id, codigoBarras, nombre, categoria, String.format("$%.2f", precio), stock}; }
    }

    static class DetalleVenta {
        Producto producto; int cantidad; double subtotal;
        public DetalleVenta(Producto p, int cant) { this.producto = p; this.cantidad = cant; recalcular(); }
        public void setCantidad(int c) { this.cantidad = c; recalcular(); }
        private void recalcular() { this.subtotal = producto.precio * cantidad; }
    }

    static class ProductoDAO {

        // --- NUEVOS MÉTODOS CRUD ---
        public List<Categoria> obtenerCategorias() throws SQLException {
            List<Categoria> list = new ArrayList<>();
            try (Connection conn = ConexionDB.getInstance().getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM Categorias")) {
                while (rs.next()) list.add(new Categoria(rs.getInt("IdCategoria"), rs.getString("Nombre")));
            }
            return list;
        }

        public void guardarProducto(Producto p, boolean esNuevo) throws SQLException {
            String sql = esNuevo ? "INSERT INTO Productos (CodigoBarras, Nombre, IdCategoria, Costo, PrecioVenta, StockActual, StockMinimo, Activo) VALUES (?,?,?,?,?,?,?,1)" :
                    "UPDATE Productos SET CodigoBarras=?, Nombre=?, IdCategoria=?, Costo=?, PrecioVenta=?, StockActual=?, StockMinimo=? WHERE IdProducto=?";
            try (Connection conn = ConexionDB.getInstance().getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, p.codigoBarras); ps.setString(2, p.nombre); ps.setInt(3, p.idCategoria);
                ps.setDouble(4, p.costo); ps.setDouble(5, p.precio); ps.setInt(6, p.stock); ps.setInt(7, p.stockMinimo);
                if (!esNuevo) ps.setInt(8, p.id);
                ps.executeUpdate();
            }
        }

        public void eliminarProducto(int idProducto) throws SQLException {
            // Eliminación lógica para no romper historial de ventas
            String sql = "UPDATE Productos SET Activo = 0 WHERE IdProducto = ?";
            try (Connection conn = ConexionDB.getInstance().getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, idProducto); ps.executeUpdate();
            }
        }
        // ---------------------------

        public List<Producto> obtenerTodos() throws SQLException {
            List<Producto> lista = new ArrayList<>(); String sql = "SELECT p.*, c.Nombre as CatNombre FROM Productos p JOIN Categorias c ON p.IdCategoria = c.IdCategoria WHERE p.Activo=1";
            try (Connection conn = ConexionDB.getInstance().getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) { while (rs.next()) lista.add(mapRow(rs)); } return lista;
        }

        public Producto buscarPorCodigo(String codigo) throws SQLException {
            String sql = "SELECT p.*, c.Nombre as CatNombre FROM Productos p JOIN Categorias c ON p.IdCategoria = c.IdCategoria WHERE p.Activo=1 AND p.CodigoBarras=?";
            try (Connection conn = ConexionDB.getInstance().getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, codigo); try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return mapRow(rs); }
            } return null;
        }

        public void actualizarInventario(int id, int cant, String tipo, String user) throws SQLException {
            String sqlMov = "INSERT INTO MovimientosInventario (IdProducto, TipoMovimiento, Cantidad, StockAnterior, StockNuevo, UsuarioResponsable) VALUES (?, ?, ?, (SELECT StockActual FROM Productos WHERE IdProducto=?), (SELECT StockActual FROM Productos WHERE IdProducto=?)+?, ?)";
            String sqlUpd = "UPDATE Productos SET StockActual = StockActual + ? WHERE IdProducto = ?";
            Connection conn = null;
            try {
                conn = ConexionDB.getInstance().getConnection(); conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(sqlMov)) { ps.setInt(1, id); ps.setString(2, tipo); ps.setInt(3, cant); ps.setInt(4, id); ps.setInt(5, id); ps.setInt(6, cant); ps.setString(7, user); ps.executeUpdate(); }
                try (PreparedStatement ps = conn.prepareStatement(sqlUpd)) { ps.setInt(1, cant); ps.setInt(2, id); ps.executeUpdate(); }
                conn.commit();
            } catch (SQLException e) { if (conn!=null) conn.rollback(); throw e; } finally { if (conn!=null) conn.setAutoCommit(true); }
        }

        public Map<String, Object> obtenerEstadisticasHoy() throws SQLException {
            Map<String, Object> stats = new HashMap<>();
            String sqlVentas = "SELECT SUM(ABS(m.Cantidad) * p.PrecioVenta) as TotalVenta, COUNT(DISTINCT m.IdMovimiento) as Transacciones FROM MovimientosInventario m JOIN Productos p ON m.IdProducto = p.IdProducto WHERE m.TipoMovimiento = 'VENTA' AND CAST(m.FechaMovimiento AS DATE) = CAST(GETDATE() AS DATE)";
            String sqlAlertas = "SELECT COUNT(*) FROM Productos WHERE StockActual <= StockMinimo AND Activo = 1";
            try (Connection conn = ConexionDB.getInstance().getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(sqlVentas); ResultSet rs = ps.executeQuery()) { if (rs.next()) { stats.put("totalVentas", rs.getDouble("TotalVenta")); stats.put("numTransacciones", rs.getInt("Transacciones")); } }
                try (PreparedStatement ps = conn.prepareStatement(sqlAlertas); ResultSet rs = ps.executeQuery()) { if (rs.next()) stats.put("stockBajo", rs.getInt(1)); }
            } return stats;
        }

        public List<Producto> obtenerAlertasStock() throws SQLException {
            List<Producto> lista = new ArrayList<>(); String sql = "SELECT p.*, c.Nombre as CatNombre FROM Productos p JOIN Categorias c ON p.IdCategoria = c.IdCategoria WHERE p.Activo=1 AND p.StockActual <= p.StockMinimo ORDER BY p.StockActual ASC";
            try (Connection conn = ConexionDB.getInstance().getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) { while (rs.next()) lista.add(mapRow(rs)); } return lista;
        }

        private Producto mapRow(ResultSet rs) throws SQLException {
            return new Producto(rs.getInt("IdProducto"), rs.getString("CodigoBarras"), rs.getString("Nombre"), rs.getString("CatNombre"), rs.getInt("IdCategoria"), rs.getDouble("Costo"), rs.getDouble("PrecioVenta"), rs.getInt("StockActual"), rs.getInt("StockMinimo"));
        }

        public void respaldarBaseDatos(String rutaDestino) throws SQLException {
            String sql = "BACKUP DATABASE PosAbarrotes TO DISK = ?";
            try (Connection conn = ConexionDB.getInstance().getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, rutaDestino); ps.executeUpdate();
            }
        }
    }

    // ==========================================
    // 4. INTERFAZ GRÁFICA (UI)
    // ==========================================
    static class LoginDialog extends JDialog {
        public LoginDialog(JFrame parent) {
            super(parent, "Iniciar Sesión", true); setSize(400, 300); setLocationRelativeTo(null); setLayout(new BorderLayout()); setResizable(false);
            JPanel panel = new JPanel(new GridBagLayout()); panel.setBackground(Config.COLOR_BG); GridBagConstraints gbc = new GridBagConstraints(); gbc.insets = new Insets(10, 10, 10, 10); gbc.fill = GridBagConstraints.HORIZONTAL;
            JLabel lblTitle = new JLabel("POS SECURITY"); lblTitle.setFont(Config.FONT_BIG); lblTitle.setHorizontalAlignment(SwingConstants.CENTER);
            JTextField txtUser = new JTextField(15); txtUser.setBorder(BorderFactory.createTitledBorder("Usuario"));
            JPasswordField txtPass = new JPasswordField(15); txtPass.setBorder(BorderFactory.createTitledBorder("Contraseña"));
            JButton btnLogin = new JButton("ENTRAR"); btnLogin.setBackground(Color.BLACK); btnLogin.setForeground(Color.BLACK); btnLogin.setFont(Config.FONT_HEADER); btnLogin.setFocusPainted(false);
            btnLogin.addActionListener(e -> {
                Usuario u = new UsuarioDAO().login(txtUser.getText(), new String(txtPass.getPassword()));
                if (u != null) { AbarrotesPos.sesionActual = u; dispose(); new MainFrame().setVisible(true); } else { JOptionPane.showMessageDialog(this, "Credenciales incorrectas.\nPrueba: admin / 1234", "Error", JOptionPane.ERROR_MESSAGE); }
            });
            gbc.gridx=0; gbc.gridy=0; panel.add(lblTitle, gbc); gbc.gridy=1; panel.add(txtUser, gbc); gbc.gridy=2; panel.add(txtPass, gbc); gbc.gridy=3; panel.add(btnLogin, gbc); add(panel);
            addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { System.exit(0); } });
        }
    }

    static class SidebarButton extends JButton {
        public SidebarButton(String text) {
            super(text); setHorizontalAlignment(SwingConstants.LEFT); setFocusPainted(false); setBorderPainted(false); setContentAreaFilled(false);
            setForeground(new Color(145, 158, 171)); setFont(Config.FONT_MAIN); setCursor(new Cursor(Cursor.HAND_CURSOR)); setBorder(new EmptyBorder(12, 20, 12, 10));
            addMouseListener(new MouseAdapter() { public void mouseEntered(MouseEvent e) { setForeground(Color.BLACK); } public void mouseExited(MouseEvent e) { setForeground(new Color(145, 158, 171)); } });
        }
    }

    static class KPICard extends JPanel {
        public KPICard(String title, String value, Color colorIcon) {
            setLayout(new BorderLayout(15, 0)); setBackground(Config.COLOR_CARD_BG); setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(230,230,230), 1), new EmptyBorder(20, 20, 20, 20)));
            JPanel iconBar = new JPanel(); iconBar.setBackground(colorIcon); iconBar.setPreferredSize(new Dimension(5, 50)); add(iconBar, BorderLayout.WEST);
            JPanel textPanel = new JPanel(new GridLayout(2, 1)); textPanel.setBackground(Config.COLOR_CARD_BG);
            JLabel lblVal = new JLabel(value); lblVal.setFont(Config.FONT_KPI_VALUE); lblVal.setForeground(Color.DARK_GRAY);
            JLabel lblTitle = new JLabel(title.toUpperCase()); lblTitle.setFont(Config.FONT_KPI_TITLE); lblTitle.setForeground(Color.GRAY);
            textPanel.add(lblVal); textPanel.add(lblTitle); add(textPanel, BorderLayout.CENTER);
        }
    }

    // --- FORMULARIO CRUD MODAL (NUEVO) ---
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

            add(new JLabel("Código Barras:")); add(txtCod); add(new JLabel("Nombre:")); add(txtNom);
            add(new JLabel("Categoría:")); add(cbCat); add(new JLabel("Costo ($):")); add(txtCosto);
            add(new JLabel("Precio Venta ($):")); add(txtPrecio); add(new JLabel("Stock Inicial:")); add(txtStock);
            add(new JLabel("Stock Mínimo:")); add(txtStockMin);

            JButton btnGuardar = new JButton("Guardar"); btnGuardar.setBackground(Color.BLACK); btnGuardar.setForeground(Color.BLACK);
            JButton btnCancelar = new JButton("Cancelar"); btnCancelar.setBackground(Color.BLACK); btnCancelar.setForeground(Color.BLACK); btnCancelar.addActionListener(e -> dispose());

            btnGuardar.addActionListener(e -> {
                try {
                    if (txtCod.getText().isEmpty() || txtNom.getText().isEmpty()) { JOptionPane.showMessageDialog(this, "Código y Nombre son obligatorios."); return; }
                    double costo = Double.parseDouble(txtCosto.getText()); double precio = Double.parseDouble(txtPrecio.getText());
                    int stock = Integer.parseInt(txtStock.getText()); int min = Integer.parseInt(txtStockMin.getText());
                    Categoria c = (Categoria) cbCat.getSelectedItem();
                    Producto nuevo = new Producto(p != null ? p.id : 0, txtCod.getText().trim(), txtNom.getText().trim(), c.nombre, c.id, costo, precio, stock, min);
                    dao.guardarProducto(nuevo, p == null);
                    guardado = true; dispose();
                } catch (NumberFormatException ex) { JOptionPane.showMessageDialog(this, "Asegúrese de ingresar números válidos en Costo, Precio y Stock.", "Error", JOptionPane.ERROR_MESSAGE); }
                catch (SQLException ex) { JOptionPane.showMessageDialog(this, "Error de Base de Datos: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
            });
            add(btnGuardar); add(btnCancelar);
        }
    }

    static class MainFrame extends JFrame {
        private JPanel mainContent; private CardLayout cardLayout;
        public MainFrame() {
            setTitle("POS Abarrotes - v8.0 Enterprise Release"); setSize(1280, 800); setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); setLocationRelativeTo(null); setLayout(new BorderLayout());
            JPanel sidebar = new JPanel(); sidebar.setBackground(Config.COLOR_SIDEBAR); sidebar.setPreferredSize(new Dimension(260, getHeight())); sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
            JLabel lblLogo = new JLabel("POS ABARROTES"); lblLogo.setForeground(Color.WHITE); lblLogo.setFont(new Font("Segoe UI", Font.BOLD, 22)); lblLogo.setBorder(new EmptyBorder(40, 30, 40, 20)); sidebar.add(lblLogo);
            boolean isAdmin = AbarrotesPos.sesionActual != null && "ADMIN".equals(AbarrotesPos.sesionActual.rol);
            if (isAdmin) addMenu(sidebar, "Dashboard (Inicio)", "DASHBOARD");
            addMenu(sidebar, "Punto de Venta (Alt+V)", "VENTAS");
            if (isAdmin) addMenu(sidebar, "Gestión de Inventario", "INVENTARIO");
            sidebar.add(Box.createVerticalGlue());
            JLabel lblUser = new JLabel("Usuario: " + (sesionActual!=null ? sesionActual.username : "N/A")); lblUser.setForeground(Color.WHITE); lblUser.setFont(new Font("Segoe UI", Font.BOLD, 14)); lblUser.setBorder(new EmptyBorder(0, 30, 5, 0));
            JLabel lblRol = new JLabel(isAdmin ? "ROL: ADMINISTRADOR" : "ROL: CAJERO"); lblRol.setForeground(Config.COLOR_SUCCESS); lblRol.setFont(new Font("Segoe UI", Font.PLAIN, 12)); lblRol.setBorder(new EmptyBorder(0, 30, 30, 0));
            sidebar.add(lblUser); sidebar.add(lblRol); add(sidebar, BorderLayout.WEST);
            cardLayout = new CardLayout(); mainContent = new JPanel(cardLayout); mainContent.setBackground(Config.COLOR_BG);
            if (isAdmin) mainContent.add(new DashboardPanel(), "DASHBOARD");
            mainContent.add(new VentasPanel(), "VENTAS");
            if (isAdmin) mainContent.add(new InventoryPanel(), "INVENTARIO");
            add(mainContent, BorderLayout.CENTER);
            if (isAdmin) cardLayout.show(mainContent, "DASHBOARD"); else cardLayout.show(mainContent, "VENTAS");
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> { if (e.getID() == KeyEvent.KEY_PRESSED && e.isAltDown() && e.getKeyCode() == KeyEvent.VK_V) { cardLayout.show(mainContent, "VENTAS"); return true; } return false; });
        }
        private void addMenu(JPanel p, String t, String c) { SidebarButton b = new SidebarButton(t); b.addActionListener(e -> cardLayout.show(mainContent, c)); b.setMaximumSize(new Dimension(260, 50)); p.add(b); }
    }

    static class DashboardPanel extends JPanel {
        private ProductoDAO dao; private JPanel kpiPanel; private JTable tableAlertas; private DefaultTableModel modelAlertas;
        public DashboardPanel() {
            setLayout(new BorderLayout(30, 30)); setBackground(Config.COLOR_BG); setBorder(new EmptyBorder(30, 30, 30, 30)); dao = new ProductoDAO();
            JLabel lblTitle = new JLabel("Resumen de Operaciones"); lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 24)); lblTitle.setForeground(Config.COLOR_SIDEBAR);
            JButton btnBackup = new JButton("Respaldar BD"); btnBackup.setFont(Config.FONT_MAIN); btnBackup.setBackground(Color.BLACK); btnBackup.setForeground(Color.BLACK); btnBackup.setFocusPainted(false); btnBackup.addActionListener(e -> ejecutarRespaldoBD());
            JButton btnRefresh = new JButton("Actualizar"); btnRefresh.setFont(Config.FONT_MAIN); btnRefresh.setBackground(Color.BLACK); btnRefresh.setForeground(Color.BLACK); btnRefresh.addActionListener(e -> cargarDatos());
            JPanel pnlBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT)); pnlBotones.setBackground(Config.COLOR_BG); pnlBotones.add(btnBackup); pnlBotones.add(btnRefresh);
            JPanel header = new JPanel(new BorderLayout()); header.setBackground(Config.COLOR_BG); header.add(lblTitle, BorderLayout.WEST); header.add(pnlBotones, BorderLayout.EAST); add(header, BorderLayout.NORTH);
            JPanel centerPanel = new JPanel(); centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS)); centerPanel.setBackground(Config.COLOR_BG);
            kpiPanel = new JPanel(new GridLayout(1, 3, 20, 0)); kpiPanel.setBackground(Config.COLOR_BG); kpiPanel.setPreferredSize(new Dimension(0, 120)); kpiPanel.setMaximumSize(new Dimension(2000, 120));
            centerPanel.add(kpiPanel); centerPanel.add(Box.createRigidArea(new Dimension(0, 30))); centerPanel.add(new JLabel("Alertas de Stock"));
            modelAlertas = new DefaultTableModel(new String[]{"ID", "Producto", "Categoría", "Stock", "Mínimo"}, 0) { @Override public boolean isCellEditable(int r,int c){return false;} };
            tableAlertas = new JTable(modelAlertas); tableAlertas.setRowHeight(30);
            tableAlertas.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() { public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) { Component cp = super.getTableCellRendererComponent(t,v,s,f,r,c); if(!s) { cp.setBackground(new Color(255, 235, 238)); cp.setForeground(Config.COLOR_DANGER); } return cp; } });
            centerPanel.add(new JScrollPane(tableAlertas)); add(centerPanel, BorderLayout.CENTER); cargarDatos();
        }

        private void cargarDatos() {
            new SwingWorker<Map<String, Object>, Void>() {
                List<Producto> alertas;
                protected Map<String, Object> doInBackground() throws Exception { alertas = dao.obtenerAlertasStock(); return dao.obtenerEstadisticasHoy(); }
                protected void done() {
                    try { Map<String, Object> stats = get(); kpiPanel.removeAll(); kpiPanel.add(new KPICard("Ventas", String.format("$%.2f", stats.get("totalVentas")!=null?stats.get("totalVentas"):0.0), Config.COLOR_SUCCESS)); kpiPanel.add(new KPICard("Transacciones", ""+(stats.get("numTransacciones")!=null?stats.get("numTransacciones"):0), Config.COLOR_INFO)); kpiPanel.add(new KPICard("Alertas", ""+(stats.get("stockBajo")!=null?stats.get("stockBajo"):0), Config.COLOR_DANGER)); kpiPanel.revalidate(); modelAlertas.setRowCount(0); for(Producto p : alertas) modelAlertas.addRow(new Object[]{p.id, p.nombre, p.categoria, p.stock, p.stockMinimo}); } catch(Exception e){}
                }
            }.execute();
        }

        private void ejecutarRespaldoBD() {
            JFileChooser chooser = new JFileChooser(); chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); chooser.setDialogTitle("Seleccione carpeta para guardar el respaldo");
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String rutaFinal = chooser.getSelectedFile().getAbsolutePath() + "\\PosAbarrotes_Backup_" + timestamp + ".bak";
                JDialog loading = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Respaldando", true); loading.add(new JLabel(" Generando respaldo en SQL Server, por favor espere... ", SwingConstants.CENTER)); loading.setSize(400, 100); loading.setLocationRelativeTo(this);
                new SwingWorker<Void, Void>() {
                    Exception error = null;
                    protected Void doInBackground() { try { dao.respaldarBaseDatos(rutaFinal); } catch (Exception e) { error = e; } return null; }
                    protected void done() { loading.dispose(); if (error == null) { JOptionPane.showMessageDialog(DashboardPanel.this, "¡Respaldo creado exitosamente!\nRuta: " + rutaFinal, "Mantenimiento Exitoso", JOptionPane.INFORMATION_MESSAGE); } else { JOptionPane.showMessageDialog(DashboardPanel.this, "Error al respaldar.\nConsejo: Asegúrese de que SQL Server tenga permisos de escritura en la carpeta seleccionada.\nDetalle: " + error.getMessage(), "Error SQL", JOptionPane.ERROR_MESSAGE); } }
                }.execute();
                loading.setVisible(true);
            }
        }
    }

    static class VentasPanel extends JPanel {
        private DefaultTableModel modelTicket; private JTextField txtCodigo; private JTextField txtCantidad; private JLabel lblTotal; private JTable table; private List<DetalleVenta> carrito; private ProductoDAO productoDAO;
        public VentasPanel() {
            setLayout(new BorderLayout(15, 15)); setBackground(Config.COLOR_BG); setBorder(new EmptyBorder(20, 20, 20, 20)); productoDAO = new ProductoDAO(); carrito = new ArrayList<>();
            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT)); topPanel.setBackground(Config.COLOR_BG);
            txtCodigo = new JTextField(15); txtCodigo.setFont(Config.FONT_BIG); txtCodigo.addActionListener(e -> agregarProducto());
            txtCantidad = new JTextField("1", 3); txtCantidad.setFont(Config.FONT_BIG); txtCantidad.setHorizontalAlignment(JTextField.CENTER); txtCantidad.addActionListener(e -> agregarProducto());
            JButton btnBuscar = new JButton("Agregar"); btnBuscar.setBackground(Color.BLACK); btnBuscar.setForeground(Color.BLACK); btnBuscar.addActionListener(e -> agregarProducto());
            topPanel.add(new JLabel("Producto:")); topPanel.add(txtCodigo); topPanel.add(Box.createRigidArea(new Dimension(15, 0))); topPanel.add(new JLabel("Cant:")); topPanel.add(txtCantidad); topPanel.add(Box.createRigidArea(new Dimension(15, 0))); topPanel.add(btnBuscar); add(topPanel, BorderLayout.NORTH);
            modelTicket = new DefaultTableModel(new String[]{"Código", "Producto", "Precio", "Cant.", "Subtotal"}, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
            table = new JTable(modelTicket); table.setRowHeight(30); table.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) editarCantidad(); } });
            add(new JScrollPane(table), BorderLayout.CENTER);
            JPanel rightPanel = new JPanel(); rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS)); rightPanel.setBackground(Color.WHITE); rightPanel.setPreferredSize(new Dimension(300, 0));
            lblTotal = new JLabel("$0.00"); lblTotal.setFont(Config.FONT_HUGE); lblTotal.setAlignmentX(CENTER_ALIGNMENT);
            JButton btnCobrar = new JButton("COBRAR"); btnCobrar.setBackground(Color.BLACK); btnCobrar.setForeground(Color.BLACK); btnCobrar.setMaximumSize(new Dimension(280, 50)); btnCobrar.setAlignmentX(CENTER_ALIGNMENT); btnCobrar.addActionListener(e -> procesarCobro());
            JButton btnEditar = new JButton("Editar Cantidad"); btnEditar.setBackground(Color.BLACK); btnEditar.setForeground(Color.BLACK); btnEditar.setMaximumSize(new Dimension(280, 40)); btnEditar.setAlignmentX(CENTER_ALIGNMENT); btnEditar.addActionListener(e -> editarCantidad());
            JButton btnEliminar = new JButton("Quitar (Del)"); btnEliminar.setBackground(Color.BLACK); btnEliminar.setForeground(Color.BLACK); btnEliminar.setMaximumSize(new Dimension(280, 40)); btnEliminar.setAlignmentX(CENTER_ALIGNMENT); btnEliminar.addActionListener(e -> eliminarProducto());
            JButton btnCancelar = new JButton("Cancelar Venta"); btnCancelar.setBackground(Color.BLACK); btnCancelar.setForeground(Color.BLACK); btnCancelar.setMaximumSize(new Dimension(280, 40)); btnCancelar.setAlignmentX(CENTER_ALIGNMENT); btnCancelar.addActionListener(e -> cancelarVenta());
            JButton btnCorte = new JButton("Corte Caja"); btnCorte.setBackground(Color.BLACK); btnCorte.setForeground(Color.BLACK); btnCorte.setMaximumSize(new Dimension(280, 40)); btnCorte.setAlignmentX(CENTER_ALIGNMENT); btnCorte.addActionListener(e -> generarCorteCaja());
            rightPanel.add(Box.createVerticalGlue()); rightPanel.add(lblTotal); rightPanel.add(Box.createRigidArea(new Dimension(0, 20))); rightPanel.add(btnCobrar); rightPanel.add(Box.createRigidArea(new Dimension(0, 20))); rightPanel.add(btnEditar); rightPanel.add(Box.createRigidArea(new Dimension(0, 10))); rightPanel.add(btnEliminar); rightPanel.add(Box.createRigidArea(new Dimension(0, 10))); rightPanel.add(btnCancelar); rightPanel.add(Box.createRigidArea(new Dimension(0, 20))); rightPanel.add(btnCorte); rightPanel.add(Box.createVerticalGlue()); add(rightPanel, BorderLayout.EAST);

            InputMap im = table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT); ActionMap am = table.getActionMap();
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "edt"); am.put("edt", new AbstractAction(){public void actionPerformed(ActionEvent e){editarCantidad();}});
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "del"); am.put("del", new AbstractAction(){public void actionPerformed(ActionEvent e){eliminarProducto();}});
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "esc"); am.put("esc", new AbstractAction(){public void actionPerformed(ActionEvent e){cancelarVenta();}});
        }

        private void agregarProducto() {
            String codigo = txtCodigo.getText().trim(); if (codigo.isEmpty()) return;
            int cantSolicitada; try { cantSolicitada = Integer.parseInt(txtCantidad.getText().trim()); if(cantSolicitada <= 0) throw new Exception(); } catch(Exception ex) { JOptionPane.showMessageDialog(this, "Cantidad inicial inválida"); return; }
            final int cFinal = cantSolicitada;
            new SwingWorker<Producto, Void>() {
                protected Producto doInBackground() throws Exception { return productoDAO.buscarPorCodigo(codigo); }
                protected void done() {
                    try { Producto p = get(); if (p != null) { if(p.stock < cFinal) { JOptionPane.showMessageDialog(VentasPanel.this, "Stock insuficiente. Disponible: " + p.stock); return; } boolean existe=false; for(DetalleVenta d:carrito) { if(d.producto.id==p.id) { if(d.cantidad + cFinal > p.stock) { JOptionPane.showMessageDialog(VentasPanel.this, "Stock insuficiente. Disponible total: " + p.stock); return; } d.setCantidad(d.cantidad+cFinal); existe=true; break; } } if(!existe) carrito.add(new DetalleVenta(p, cFinal)); actualizarTabla(); txtCodigo.setText(""); txtCantidad.setText("1"); txtCodigo.requestFocus(); } else JOptionPane.showMessageDialog(VentasPanel.this, "No encontrado"); } catch(Exception e){}
                }
            }.execute();
        }

        private void editarCantidad() {
            int r = table.getSelectedRow(); if(r==-1) { JOptionPane.showMessageDialog(this, "Seleccione un producto."); return; }
            DetalleVenta d = carrito.get(r); String in = JOptionPane.showInputDialog(this, "Stock: " + d.producto.stock + "\nNueva Cantidad:", d.cantidad);
            if(in!=null) { try { int c=Integer.parseInt(in); if(c>0 && c<=d.producto.stock) { d.setCantidad(c); actualizarTabla(); } else JOptionPane.showMessageDialog(this, "Stock insuficiente"); } catch(Exception e){ JOptionPane.showMessageDialog(this, "Número inválido"); } }
        }
        private void eliminarProducto() { int r = table.getSelectedRow(); if(r!=-1 && JOptionPane.showConfirmDialog(this, "¿Eliminar?", "Conf", JOptionPane.YES_NO_OPTION)==0) { carrito.remove(r); actualizarTabla(); } }
        private void cancelarVenta() { if(!carrito.isEmpty() && JOptionPane.showConfirmDialog(this, "¿Cancelar venta?", "Conf", JOptionPane.YES_NO_OPTION)==0) { carrito.clear(); actualizarTabla(); txtCodigo.requestFocus(); } }
        private void actualizarTabla() { modelTicket.setRowCount(0); double t=0; for(DetalleVenta d:carrito) { modelTicket.addRow(new Object[]{d.producto.codigoBarras, d.producto.nombre, d.producto.precio, d.cantidad, d.subtotal}); t+=d.subtotal; } lblTotal.setText(String.format("$%.2f", t)); }
        private void procesarCobro() {
            if(carrito.isEmpty()) return; double tot = carrito.stream().mapToDouble(d->d.subtotal).sum(); String pStr = JOptionPane.showInputDialog("Total: $"+tot+"\nPago:");
            if(pStr!=null) { try { double pay = Double.parseDouble(pStr); if(pay>=tot) {
                for(DetalleVenta d:carrito) productoDAO.actualizarInventario(d.producto.id, -d.cantidad, "VENTA", AbarrotesPos.sesionActual.username);
                PdfService.generarTicketPDF(new ArrayList<>(carrito), tot, pay, pay-tot);
                JOptionPane.showMessageDialog(this, "Cambio: $" + String.format("%.2f", pay - tot), "Venta Exitosa", JOptionPane.INFORMATION_MESSAGE);
                carrito.clear(); actualizarTabla(); txtCantidad.setText("1"); txtCodigo.requestFocus();
            } else JOptionPane.showMessageDialog(this, "Pago insuficiente"); } catch(Exception e) { JOptionPane.showMessageDialog(this, "Error en pago"); } }
        }
        private void generarCorteCaja() { try { Map<String, Object> stats = productoDAO.obtenerEstadisticasHoy(); PdfService.generarCortePDF(stats); JOptionPane.showMessageDialog(this, "Corte Generado en carpeta /Cortes"); } catch(Exception e){} }
    }

    // --- INVENTORY PANEL (ACTUALIZADO: CRUD COMPLETO) ---
    static class InventoryPanel extends JPanel {
        private DefaultTableModel model; private ProductoDAO dao = new ProductoDAO(); private JTable table; private List<Producto> listaActual;
        public InventoryPanel() {
            setLayout(new BorderLayout(15, 15)); setBackground(Config.COLOR_BG); setBorder(new EmptyBorder(20, 20, 20, 20));

            // Botonera Superior
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); top.setBackground(Config.COLOR_BG);
            JButton btnNuevo = new JButton("Nuevo Producto"); btnNuevo.setBackground(Color.BLACK); btnNuevo.setForeground(Color.BLACK); btnNuevo.addActionListener(e -> gestionarProducto(null));
            JButton btnEditar = new JButton("Editar Selección"); btnEditar.setBackground(Color.BLACK); btnEditar.setForeground(Color.BLACK); btnEditar.addActionListener(e -> editarProducto());
            JButton btnEliminar = new JButton("Eliminar"); btnEliminar.setBackground(Color.BLACK); btnEliminar.setForeground(Color.BLACK); btnEliminar.addActionListener(e -> eliminarProducto());
            JButton btnRecargar = new JButton("Recargar"); btnRecargar.setBackground(Color.BLACK); btnRecargar.setForeground(Color.BLACK); btnRecargar.addActionListener(e -> cargar());

            top.add(btnNuevo); top.add(btnEditar); top.add(btnEliminar); top.add(btnRecargar);
            add(top, BorderLayout.NORTH);

            model = new DefaultTableModel(new String[]{"ID","Código","Producto","Categoría","Precio","Stock"}, 0) { @Override public boolean isCellEditable(int r,int c){return false;} };
            table = new JTable(model); table.setRowHeight(30); table.setFont(Config.FONT_MAIN); table.getTableHeader().setFont(Config.FONT_HEADER);
            add(new JScrollPane(table), BorderLayout.CENTER);
            cargar();
        }

        private void cargar() {
            new SwingWorker<List<Producto>, Void>() {
                protected List<Producto> doInBackground() throws Exception { return dao.obtenerTodos(); }
                protected void done() {
                    try {
                        listaActual = get(); model.setRowCount(0);
                        for(Producto p:listaActual) model.addRow(p.toRow());
                    } catch(Exception e){}
                }
            }.execute();
        }

        private void gestionarProducto(Producto p) {
            new SwingWorker<List<Categoria>, Void>() {
                protected List<Categoria> doInBackground() throws Exception { return dao.obtenerCategorias(); }
                protected void done() {
                    try {
                        List<Categoria> cats = get();
                        if(cats.isEmpty()) { JOptionPane.showMessageDialog(InventoryPanel.this, "Debe existir al menos 1 categoría en la BD."); return; }
                        ProductoDialog dlg = new ProductoDialog(SwingUtilities.getWindowAncestor(InventoryPanel.this), p, cats, dao);
                        dlg.setVisible(true);
                        if(dlg.guardado) cargar();
                    } catch(Exception e) { JOptionPane.showMessageDialog(InventoryPanel.this, "Error al cargar categorías: " + e.getMessage()); }
                }
            }.execute();
        }

        private void editarProducto() {
            int r = table.getSelectedRow(); if(r==-1) { JOptionPane.showMessageDialog(this, "Seleccione un producto para editar."); return; }
            gestionarProducto(listaActual.get(r));
        }

        private void eliminarProducto() {
            int r = table.getSelectedRow(); if(r==-1) { JOptionPane.showMessageDialog(this, "Seleccione un producto para eliminar."); return; }
            Producto p = listaActual.get(r);
            if(JOptionPane.showConfirmDialog(this, "¿Está seguro de eliminar el producto: " + p.nombre + "?\n\n(Se desactivará para no afectar el historial de ventas)", "Confirmar Eliminación", JOptionPane.YES_NO_OPTION) == 0) {
                new SwingWorker<Void, Void>() {
                    Exception error;
                    protected Void doInBackground() { try { dao.eliminarProducto(p.id); } catch(Exception e) { error = e; } return null; }
                    protected void done() { if(error!=null) JOptionPane.showMessageDialog(InventoryPanel.this, "Error: " + error.getMessage()); else cargar(); }
                }.execute();
            }
        }
    }
}