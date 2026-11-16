import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.Timer;

public class AgendaApp extends JFrame {

    private static final Color PRIMARY_BLUE = new Color(0, 120, 215);
    private static final Color DARK_BLUE = new Color(0, 90, 158);
    private static final Color BG_LIGHT = new Color(243, 246, 250);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color BORDER_COLOR = new Color(218, 224, 230);

    private static final DateTimeFormatter BR_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final File FILE_CONFIG = new File("agenda_config.properties");
    private static final File FILE_COMPROMISSOS = new File("agenda_compromissos.txt");
    private static final File FILE_NOTAS = new File("agenda_notas.txt");

    private JLabel lblLogo;
    private JLabel lblEmpresaNome;
    private JLabel lblRelogio;
    private JTextField txtBusca;
    private DefaultTableModel tableModel;
    private JTable tableCompromissos;
    private DefaultListModel<NotaItem> notasModel;
    private JList<NotaItem> listNotas;
    private JButton[] btnAtalhos = new JButton[5];
    private TrayIcon trayIcon;

    private List<Compromisso> listaCompromissos = new ArrayList<>();
    private List<NotaItem> listaNotas = new ArrayList<>();
    private Properties configProps = new Properties();

    public AgendaApp() {
        carregarDadosDisco();

        setTitle("Agenda Corporativa Moderna");
        setSize(1200, 760);
        setMinimumSize(new Dimension(1024, 680));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (SystemTray.isSupported() && trayIcon != null) {
                    setVisible(false);
                } else {
                    System.exit(0);
                }
            }
        });

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(BG_LIGHT);
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(root);

        root.add(criarCabecalho(), BorderLayout.NORTH);
        root.add(criarPainelCentral(), BorderLayout.CENTER);
        root.add(criarPainelLateralAtalhos(), BorderLayout.EAST);

        configurarSystemTray();
        iniciarRelogio();
        iniciarMonitorAlertas();
        atualizarInterfaceCompleta();
    }

    private void carregarDadosDisco() {
        // Configuracoes e Atalhos
        if (FILE_CONFIG.exists()) {
            try (InputStream in = new FileInputStream(FILE_CONFIG)) {
                configProps.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
        }
        configProps.putIfAbsent("empresa_nome", "Agenda Corporativa");
        configProps.putIfAbsent("empresa_logo", "");
        for (int i = 1; i <= 5; i++) {
            configProps.putIfAbsent("atalho_" + i + "_nome", "Atalho " + i);
            configProps.putIfAbsent("atalho_" + i + "_caminho", "");
        }
        salvarConfigDisco();

        // Compromissos
        listaCompromissos.clear();
        if (FILE_COMPROMISSOS.exists()) {
            try {
                List<String> lines = Files.readAllLines(FILE_COMPROMISSOS.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;
                    String[] p = line.split("\\|", -1);
                    if (p.length >= 9) {
                        Compromisso c = new Compromisso();
                        c.id = Integer.parseInt(p[0]);
                        c.titulo = decode(p[1]);
                        c.dataHora = LocalDateTime.parse(p[2], ISO_FMT);
                        c.local = decode(p[3]);
                        c.categoria = decode(p[4]);
                        c.prioridade = decode(p[5]);
                        c.observacoes = decode(p[6]);
                        c.alertasMinutos = p[7];
                        c.alertasDisparados = p[8];
                        c.concluido = p.length > 9 && "1".equals(p[9]);
                        listaCompromissos.add(c);
                    }
                }
            } catch (Exception ignored) {}
        }

        // Notas Permanentes
        listaNotas.clear();
        if (FILE_NOTAS.exists()) {
            try {
                List<String> lines = Files.readAllLines(FILE_NOTAS.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;
                    String[] p = line.split("\\|", -1);
                    if (p.length >= 3) {
                        listaNotas.add(new NotaItem(Integer.parseInt(p[0]), decode(p[1]), "1".equals(p[2])));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void salvarConfigDisco() {
        try (OutputStream out = new FileOutputStream(FILE_CONFIG)) {
            configProps.store(new OutputStreamWriter(out, StandardCharsets.UTF_8), "Configuracoes Agenda");
        } catch (Exception ignored) {}
    }

    private synchronized void salvarCompromissosDisco() {
        List<String> lines = new ArrayList<>();
        for (Compromisso c : listaCompromissos) {
            lines.add(c.id + "|" + encode(c.titulo) + "|" + c.dataHora.format(ISO_FMT) + "|" +
                    encode(c.local) + "|" + encode(c.categoria) + "|" + encode(c.prioridade) + "|" +
                    encode(c.observacoes) + "|" + c.alertasMinutos + "|" + c.alertasDisparados + "|" +
                    (c.concluido ? "1" : "0"));
        }
        try {
            Files.write(FILE_COMPROMISSOS.toPath(), lines, StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    private synchronized void salvarNotasDisco() {
        List<String> lines = new ArrayList<>();
        for (NotaItem n : listaNotas) {
            lines.add(n.id + "|" + encode(n.conteudo) + "|" + (n.concluido ? "1" : "0"));
        }
        try {
            Files.write(FILE_NOTAS.toPath(), lines, StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    private String encode(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n").replace("\r", "");
    }

    private String decode(String s) {
        if (s == null) return "";
        return s.replace("\\n", "\n").replace("\\p", "|").replace("\\\\", "\\");
    }

    private JPanel criarCabecalho() {
        JPanel header = new JPanel(new BorderLayout(15, 0));
        header.setBackground(PRIMARY_BLUE);
        header.setBorder(new EmptyBorder(12, 18, 12, 18));

        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        leftHeader.setOpaque(false);

        lblLogo = new JLabel("LOGO", SwingConstants.CENTER);
        lblLogo.setPreferredSize(new Dimension(120, 48));
        lblLogo.setOpaque(true);
        lblLogo.setBackground(Color.WHITE);
        lblLogo.setForeground(PRIMARY_BLUE);
        lblLogo.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblLogo.setBorder(new LineBorder(Color.WHITE, 2));

        lblEmpresaNome = new JLabel("Agenda Corporativa");
        lblEmpresaNome.setForeground(Color.WHITE);
        lblEmpresaNome.setFont(new Font("Segoe UI", Font.BOLD, 22));

        leftHeader.add(lblLogo);
        leftHeader.add(lblEmpresaNome);

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        rightHeader.setOpaque(false);

        lblRelogio = new JLabel();
        lblRelogio.setForeground(Color.WHITE);
        lblRelogio.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblRelogio.setBorder(new EmptyBorder(0, 0, 0, 15));

        JButton btnConfig = criarBotaoHeader("Configurar Empresa / Logo");
        btnConfig.addActionListener(e -> abrirDialogoConfiguracoes());

        JButton btnBackup = criarBotaoHeader("Backup / Restaurar");
        btnBackup.addActionListener(e -> abrirDialogoBackup());

        rightHeader.add(lblRelogio);
        rightHeader.add(btnConfig);
        rightHeader.add(btnBackup);

        header.add(leftHeader, BorderLayout.WEST);
        header.add(rightHeader, BorderLayout.EAST);
        return header;
    }

    private JButton criarBotaoHeader(String texto) {
        JButton btn = new JButton(texto);
        btn.setBackground(DARK_BLUE);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBorder(new EmptyBorder(8, 14, 8, 14));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JPanel criarPainelCentral() {
        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setOpaque(false);

        JPanel topBar = new JPanel(new BorderLayout(10, 0));
        topBar.setBackground(CARD_BG);
        topBar.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR), new EmptyBorder(10, 12, 10, 12)));

        JPanel botoesAcao = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        botoesAcao.setOpaque(false);

        JButton btnNovo = new JButton("+ Novo Compromisso");
        btnNovo.setBackground(PRIMARY_BLUE);
        btnNovo.setForeground(Color.WHITE);
        btnNovo.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnNovo.setFocusPainted(false);
        btnNovo.addActionListener(e -> abrirFormularioCompromisso());

        JButton btnConcluir = new JButton("Concluir / Reabrir");
        btnConcluir.addActionListener(e -> alternarConclusaoCompromisso());

        JButton btnExcluir = new JButton("Excluir");
        btnExcluir.addActionListener(e -> excluirCompromisso());

        botoesAcao.add(btnNovo);
        botoesAcao.add(btnConcluir);
        botoesAcao.add(btnExcluir);

        JPanel buscaPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buscaPanel.setOpaque(false);
        buscaPanel.add(new JLabel("Pesquisar:"));
        txtBusca = new JTextField(18);
        txtBusca.addActionListener(e -> atualizarTabelaCompromissos());
        JButton btnBuscar = new JButton("Filtrar");
        btnBuscar.addActionListener(e -> atualizarTabelaCompromissos());
        buscaPanel.add(txtBusca);
        buscaPanel.add(btnBuscar);

        topBar.add(botoesAcao, BorderLayout.WEST);
        topBar.add(buscaPanel, BorderLayout.EAST);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.68);
        splitPane.setDividerSize(8);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);

        String[] colunas = {"ID", "Status", "Data/Hora", "Título", "Categoria", "Prioridade", "Local", "Alertas"};
        tableModel = new DefaultTableModel(colunas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tableCompromissos = new JTable(tableModel);
        tableCompromissos.setRowHeight(28);
        tableCompromissos.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tableCompromissos.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tableCompromissos.getColumnModel().getColumn(0).setMaxWidth(45);
        tableCompromissos.getColumnModel().getColumn(1).setMaxWidth(85);

        tableCompromissos.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    String status = (String) table.getValueAt(row, 1);
                    String prioridade = (String) table.getValueAt(row, 5);
                    String dataHoraStr = (String) table.getValueAt(row, 2);
                    String hojeStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

                    if ("Concluído".equals(status)) {
                        c.setBackground(new Color(235, 235, 235));
                        c.setForeground(Color.GRAY);
                    } else if ("Urgente".equalsIgnoreCase(prioridade)) {
                        c.setBackground(new Color(255, 224, 224));
                        c.setForeground(new Color(160, 0, 0));
                    } else if (dataHoraStr != null && dataHoraStr.startsWith(hojeStr)) {
                        c.setBackground(new Color(255, 249, 214));
                        c.setForeground(new Color(120, 90, 0));
                    } else {
                        c.setBackground(new Color(232, 244, 255));
                        c.setForeground(new Color(0, 65, 130));
                    }
                }
                return c;
            }
        });

        JScrollPane scrollTable = new JScrollPane(tableCompromissos);
        scrollTable.setBorder(BorderFactory.createTitledBorder("Compromissos Agendados"));
        scrollTable.setBackground(CARD_BG);

        JPanel notasPanel = new JPanel(new BorderLayout(8, 8));
        notasPanel.setBackground(CARD_BG);
        notasPanel.setBorder(BorderFactory.createTitledBorder("Anotações & Itens Permanentes"));

        notasModel = new DefaultListModel<>();
        listNotas = new JList<>(notasModel);
        listNotas.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        listNotas.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                NotaItem item = (NotaItem) value;
                if (item.concluido) {
                    label.setText("<html><strike>" + item.conteudo + "</strike></html>");
                    label.setForeground(Color.GRAY);
                } else {
                    label.setText("• " + item.conteudo);
                }
                label.setBorder(new EmptyBorder(4, 6, 4, 6));
                return label;
            }
        });

        JPanel notaAddPanel = new JPanel(new BorderLayout(5, 5));
        notaAddPanel.setOpaque(false);
        JTextField txtNovaNota = new JTextField();
        JButton btnAddNota = new JButton("+");
        btnAddNota.setBackground(PRIMARY_BLUE);
        btnAddNota.setForeground(Color.WHITE);
        btnAddNota.addActionListener(e -> {
            String texto = txtNovaNota.getText().trim();
            if (!texto.isEmpty()) {
                int nextId = listaNotas.stream().mapToInt(n -> n.id).max().orElse(0) + 1;
                listaNotas.add(0, new NotaItem(nextId, texto, false));
                salvarNotasDisco();
                atualizarListaNotas();
                txtNovaNota.setText("");
            }
        });
        notaAddPanel.add(txtNovaNota, BorderLayout.CENTER);
        notaAddPanel.add(btnAddNota, BorderLayout.EAST);

        JPanel notaBotoesPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        notaBotoesPanel.setOpaque(false);
        JButton btnCheckNota = new JButton("Marcar Concluído");
        btnCheckNota.addActionListener(e -> {
            NotaItem item = listNotas.getSelectedValue();
            if (item != null) {
                item.concluido = !item.concluido;
                salvarNotasDisco();
                atualizarListaNotas();
            }
        });
        JButton btnDelNota = new JButton("Remover");
        btnDelNota.addActionListener(e -> {
            NotaItem item = listNotas.getSelectedValue();
            if (item != null) {
                listaNotas.remove(item);
                salvarNotasDisco();
                atualizarListaNotas();
            }
        });
        notaBotoesPanel.add(btnCheckNota);
        notaBotoesPanel.add(btnDelNota);

        JPanel notaBottom = new JPanel(new BorderLayout(5, 5));
        notaBottom.setOpaque(false);
        notaBottom.add(notaAddPanel, BorderLayout.NORTH);
        notaBottom.add(notaBotoesPanel, BorderLayout.SOUTH);

        notasPanel.add(new JScrollPane(listNotas), BorderLayout.CENTER);
        notasPanel.add(notaBottom, BorderLayout.SOUTH);

        splitPane.setLeftComponent(scrollTable);
        splitPane.setRightComponent(notasPanel);

        center.add(topBar, BorderLayout.NORTH);
        center.add(splitPane, BorderLayout.CENTER);
        return center;
    }

    private JPanel criarPainelLateralAtalhos() {
        JPanel side = new JPanel(new BorderLayout(8, 8));
        side.setBackground(CARD_BG);
        side.setPreferredSize(new Dimension(210, 0));
        side.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR), new EmptyBorder(10, 10, 10, 10)));

        JLabel titulo = new JLabel("Atalhos Rápidos", SwingConstants.CENTER);
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titulo.setForeground(PRIMARY_BLUE);
        side.add(titulo, BorderLayout.NORTH);

        JPanel gridApps = new JPanel(new GridLayout(5, 1, 8, 8));
        gridApps.setOpaque(false);
        for (int i = 0; i < 5; i++) {
            final int slot = i + 1;
            btnAtalhos[i] = new JButton("Atalho " + slot);
            btnAtalhos[i].setFont(new Font("Segoe UI", Font.BOLD, 12));
            btnAtalhos[i].setBackground(new Color(240, 245, 250));
            btnAtalhos[i].setFocusPainted(false);
            btnAtalhos[i].setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnAtalhos[i].addActionListener(e -> executarAtalho(slot));
            gridApps.add(btnAtalhos[i]);
        }
        side.add(gridApps, BorderLayout.CENTER);

        JButton btnConfigAtalhos = new JButton("Configurar Atalhos");
        btnConfigAtalhos.setBackground(PRIMARY_BLUE);
        btnConfigAtalhos.setForeground(Color.WHITE);
        btnConfigAtalhos.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnConfigAtalhos.addActionListener(e -> abrirConfiguracaoAtalhos());
        side.add(btnConfigAtalhos, BorderLayout.SOUTH);

        return side;
    }

    private void atualizarInterfaceCompleta() {
        lblEmpresaNome.setText(configProps.getProperty("empresa_nome", "Agenda Corporativa"));
        String logoPath = configProps.getProperty("empresa_logo", "");
        if (!logoPath.isEmpty() && new File(logoPath).exists()) {
            ImageIcon icon = new ImageIcon(logoPath);
            Image scaled = icon.getImage().getScaledInstance(116, 44, Image.SCALE_SMOOTH);
            lblLogo.setIcon(new ImageIcon(scaled));
            lblLogo.setText("");
        } else {
            lblLogo.setIcon(null);
            lblLogo.setText("LOGO");
        }

        for (int i = 1; i <= 5; i++) {
            String nome = configProps.getProperty("atalho_" + i + "_nome", "Atalho " + i);
            String cam = configProps.getProperty("atalho_" + i + "_caminho", "");
            btnAtalhos[i - 1].setText(nome);
            btnAtalhos[i - 1].setToolTipText(cam.isEmpty() ? "Não configurado" : cam);
        }

        atualizarTabelaCompromissos();
        atualizarListaNotas();
    }

    private void atualizarTabelaCompromissos() {
        tableModel.setRowCount(0);
        String filtro = txtBusca == null ? "" : txtBusca.getText().trim().toLowerCase();

        listaCompromissos.sort((a, b) -> {
            if (a.concluido != b.concluido) return a.concluido ? 1 : -1;
            return a.dataHora.compareTo(b.dataHora);
        });

        for (Compromisso c : listaCompromissos) {
            if (!filtro.isEmpty()) {
                boolean match = c.titulo.toLowerCase().contains(filtro) ||
                        c.categoria.toLowerCase().contains(filtro) ||
                        c.local.toLowerCase().contains(filtro);
                if (!match) continue;
            }
            int qtdAlertas = (c.alertasMinutos == null || c.alertasMinutos.isEmpty()) ? 0 : c.alertasMinutos.split(",").length;
            tableModel.addRow(new Object[]{
                    c.id,
                    c.concluido ? "Concluído" : "Pendente",
                    c.dataHora.format(BR_FMT),
                    c.titulo,
                    c.categoria,
                    c.prioridade,
                    c.local,
                    qtdAlertas + " alerta(s)"
            });
        }
    }

    private void atualizarListaNotas() {
        notasModel.clear();
        for (NotaItem n : listaNotas) {
            notasModel.addElement(n);
        }
    }

    private void iniciarRelogio() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy  HH:mm:ss");
        javax.swing.Timer t = new javax.swing.Timer(1000, e -> lblRelogio.setText(LocalDateTime.now().format(fmt)));
        t.start();
    }

    private void abrirDialogoConfiguracoes() {
        JTextField txtNome = new JTextField(lblEmpresaNome.getText(), 20);
        JTextField txtLogo = new JTextField(configProps.getProperty("empresa_logo", ""), 20);
        JButton btnProcurar = new JButton("Selecionar Imagem...");
        btnProcurar.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                txtLogo.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });

        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
        panel.add(new JLabel("Nome da Empresa:"));
        panel.add(txtNome);
        panel.add(new JLabel("Caminho do Logotipo (PNG/JPG):"));
        panel.add(txtLogo);
        panel.add(btnProcurar);

        if (JOptionPane.showConfirmDialog(this, panel, "Configurações da Empresa", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            configProps.setProperty("empresa_nome", txtNome.getText().trim());
            configProps.setProperty("empresa_logo", txtLogo.getText().trim());
            salvarConfigDisco();
            atualizarInterfaceCompleta();
        }
    }

    private void abrirConfiguracaoAtalhos() {
        JPanel panel = new JPanel(new GridLayout(6, 3, 6, 6));
        panel.add(new JLabel("Slot"));
        panel.add(new JLabel("Nome do Botão"));
        panel.add(new JLabel("Arquivo (.exe / .jar)"));

        JTextField[] nomes = new JTextField[5];
        JTextField[] caminhos = new JTextField[5];

        for (int i = 0; i < 5; i++) {
            int slot = i + 1;
            nomes[i] = new JTextField(configProps.getProperty("atalho_" + slot + "_nome", "Atalho " + slot), 12);
            caminhos[i] = new JTextField(configProps.getProperty("atalho_" + slot + "_caminho", ""), 18);

            JPanel fileBox = new JPanel(new BorderLayout(2, 0));
            fileBox.add(caminhos[i], BorderLayout.CENTER);
            JButton btnBrowse = new JButton("...");
            final int idx = i;
            btnBrowse.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    caminhos[idx].setText(fc.getSelectedFile().getAbsolutePath());
                }
            });
            fileBox.add(btnBrowse, BorderLayout.EAST);

            panel.add(new JLabel("App " + slot));
            panel.add(nomes[i]);
            panel.add(fileBox);
        }

        if (JOptionPane.showConfirmDialog(this, panel, "Configurar 5 Atalhos Externos", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            for (int i = 0; i < 5; i++) {
                int slot = i + 1;
                configProps.setProperty("atalho_" + slot + "_nome", nomes[i].getText().trim());
                configProps.setProperty("atalho_" + slot + "_caminho", caminhos[i].getText().trim());
            }
            salvarConfigDisco();
            atualizarInterfaceCompleta();
        }
    }

    private void executarAtalho(int slot) {
        String caminho = configProps.getProperty("atalho_" + slot + "_caminho", "");
        if (caminho == null || caminho.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Este atalho ainda não foi configurado. Clique em 'Configurar Atalhos'.");
            return;
        }
        try {
            File f = new File(caminho);
            if (!f.exists()) {
                JOptionPane.showMessageDialog(this, "Arquivo não encontrado: " + caminho, "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (caminho.toLowerCase().endsWith(".jar")) {
                new ProcessBuilder("javaw", "-jar", f.getAbsolutePath()).directory(f.getParentFile()).start();
            } else {
                new ProcessBuilder(f.getAbsolutePath()).directory(f.getParentFile()).start();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao abrir aplicação: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void abrirFormularioCompromisso() {
        JTextField txtTitulo = new JTextField(22);
        JTextField txtData = new JTextField(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), 10);
        JTextField txtHora = new JTextField("09:00", 6);
        JTextField txtLocal = new JTextField(22);
        JComboBox<String> cbCategoria = new JComboBox<>(new String[]{"Trabalho", "Pessoal", "Saúde", "Financeiro", "Outros"});
        JComboBox<String> cbPrioridade = new JComboBox<>(new String[]{"Normal", "Alta", "Urgente", "Baixa"});
        JTextArea txtObs = new JTextArea(3, 22);

        String[] labelsAlertas = {"30 dias", "15 dias", "7 dias", "3 dias", "1 dia", "12 horas", "1 hora", "30 min", "15 min", "10 min", "5 min"};
        long[] valoresMin = {43200, 21600, 10080, 4320, 1440, 720, 60, 30, 15, 10, 5};
        JCheckBox[] checks = new JCheckBox[labelsAlertas.length];
        JPanel alertasPanel = new JPanel(new GridLayout(3, 4, 4, 4));
        alertasPanel.setBorder(BorderFactory.createTitledBorder("Avisar com Antecedência"));
        for (int i = 0; i < labelsAlertas.length; i++) {
            checks[i] = new JCheckBox(labelsAlertas[i]);
            if (valoresMin[i] == 60 || valoresMin[i] == 15) checks[i].setSelected(true);
            alertasPanel.add(checks[i]);
        }

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; form.add(new JLabel("Título:*"), gbc);
        gbc.gridx = 1; form.add(txtTitulo, gbc);

        JPanel dtPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        dtPanel.add(txtData);
        dtPanel.add(new JLabel("Hora:"));
        dtPanel.add(txtHora);
        gbc.gridx = 0; gbc.gridy = 1; form.add(new JLabel("Data (dd/MM/aaaa):*"), gbc);
        gbc.gridx = 1; form.add(dtPanel, gbc);

        gbc.gridx = 0; gbc.gridy = 2; form.add(new JLabel("Local:"), gbc);
        gbc.gridx = 1; form.add(txtLocal, gbc);

        gbc.gridx = 0; gbc.gridy = 3; form.add(new JLabel("Categoria:"), gbc);
        gbc.gridx = 1; form.add(cbCategoria, gbc);

        gbc.gridx = 0; gbc.gridy = 4; form.add(new JLabel("Prioridade:"), gbc);
        gbc.gridx = 1; form.add(cbPrioridade, gbc);

        gbc.gridx = 0; gbc.gridy = 5; form.add(new JLabel("Observações:"), gbc);
        gbc.gridx = 1; form.add(new JScrollPane(txtObs), gbc);

        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2; form.add(alertasPanel, gbc);

        if (JOptionPane.showConfirmDialog(this, form, "Cadastrar Compromisso", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            try {
                String titulo = txtTitulo.getText().trim();
                if (titulo.isEmpty()) throw new Exception("O título é obrigatório.");

                LocalDateTime dt = LocalDateTime.parse(txtData.getText().trim() + " " + txtHora.getText().trim(), BR_FMT);
                List<String> mins = new ArrayList<>();
                for (int i = 0; i < checks.length; i++) {
                    if (checks[i].isSelected()) mins.add(String.valueOf(valoresMin[i]));
                }

                Compromisso c = new Compromisso();
                c.id = listaCompromissos.stream().mapToInt(x -> x.id).max().orElse(0) + 1;
                c.titulo = titulo;
                c.dataHora = dt;
                c.local = txtLocal.getText().trim();
                c.categoria = (String) cbCategoria.getSelectedItem();
                c.prioridade = (String) cbPrioridade.getSelectedItem();
                c.observacoes = txtObs.getText().trim();
                c.alertasMinutos = String.join(",", mins);
                c.alertasDisparados = "";
                c.concluido = false;

                listaCompromissos.add(c);
                salvarCompromissosDisco();
                atualizarTabelaCompromissos();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erro ao salvar: " + ex.getMessage(), "Atenção", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void alternarConclusaoCompromisso() {
        int row = tableCompromissos.getSelectedRow();
        if (row < 0) return;
        int id = (int) tableModel.getValueAt(row, 0);
        for (Compromisso c : listaCompromissos) {
            if (c.id == id) {
                c.concluido = !c.concluido;
                break;
            }
        }
        salvarCompromissosDisco();
        atualizarTabelaCompromissos();
    }

    private void excluirCompromisso() {
        int row = tableCompromissos.getSelectedRow();
        if (row < 0) return;
        int id = (int) tableModel.getValueAt(row, 0);
        if (JOptionPane.showConfirmDialog(this, "Deseja excluir este compromisso?", "Confirmar", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            listaCompromissos.removeIf(c -> c.id == id);
            salvarCompromissosDisco();
            atualizarTabelaCompromissos();
        }
    }

    private void abrirDialogoBackup() {
        String[] opcoes = {"Fazer Backup", "Restaurar Backup", "Cancelar"};
        int escolha = JOptionPane.showOptionDialog(this, "Backup dos dados da Agenda (Compromissos, Notas e Configurações):",
                "Backup", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, opcoes, opcoes[0]);

        if (escolha == 0) {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    File dir = fc.getSelectedFile();
                    if (FILE_CONFIG.exists()) Files.copy(FILE_CONFIG.toPath(), new File(dir, "agenda_config.properties").toPath(), StandardCopyOption.REPLACE_EXISTING);
                    if (FILE_COMPROMISSOS.exists()) Files.copy(FILE_COMPROMISSOS.toPath(), new File(dir, "agenda_compromissos.txt").toPath(), StandardCopyOption.REPLACE_EXISTING);
                    if (FILE_NOTAS.exists()) Files.copy(FILE_NOTAS.toPath(), new File(dir, "agenda_notas.txt").toPath(), StandardCopyOption.REPLACE_EXISTING);
                    JOptionPane.showMessageDialog(this, "Backup salvo na pasta selecionada!");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Erro no backup: " + ex.getMessage());
                }
            }
        } else if (escolha == 1) {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    File dir = fc.getSelectedFile();
                    File bCfg = new File(dir, "agenda_config.properties");
                    File bComp = new File(dir, "agenda_compromissos.txt");
                    File bNot = new File(dir, "agenda_notas.txt");
                    if (bCfg.exists()) Files.copy(bCfg.toPath(), FILE_CONFIG.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    if (bComp.exists()) Files.copy(bComp.toPath(), FILE_COMPROMISSOS.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    if (bNot.exists()) Files.copy(bNot.toPath(), FILE_NOTAS.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    carregarDadosDisco();
                    atualizarInterfaceCompleta();
                    JOptionPane.showMessageDialog(this, "Backup restaurado com sucesso!");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Erro ao restaurar: " + ex.getMessage());
                }
            }
        }
    }

    private void configurarSystemTray() {
        if (!SystemTray.isSupported()) return;
        SystemTray tray = SystemTray.getSystemTray();

        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(PRIMARY_BLUE);
        g2.fillRoundRect(0, 0, 16, 16, 4, 4);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.drawString("A", 4, 12);
        g2.dispose();

        PopupMenu popup = new PopupMenu();
        MenuItem abrirItem = new MenuItem("Abrir Agenda");
        abrirItem.addActionListener(e -> SwingUtilities.invokeLater(() -> {
            setVisible(true);
            setExtendedState(JFrame.NORMAL);
            toFront();
        }));

        MenuItem sairItem = new MenuItem("Encerrar Completamente");
        sairItem.addActionListener(e -> System.exit(0));

        popup.add(abrirItem);
        popup.addSeparator();
        popup.add(sairItem);

        trayIcon = new TrayIcon(img, "Agenda Corporativa", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> SwingUtilities.invokeLater(() -> {
            setVisible(true);
            setExtendedState(JFrame.NORMAL);
            toFront();
        }));

        try {
            tray.add(trayIcon);
        } catch (AWTException ignored) {}
    }

    private void iniciarMonitorAlertas() {
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                verificarAlertas();
            }
        }, 5000, 30000);
    }

    private synchronized void verificarAlertas() {
        LocalDateTime now = LocalDateTime.now();
        boolean alterado = false;

        for (Compromisso c : listaCompromissos) {
            if (c.concluido || c.alertasMinutos == null || c.alertasMinutos.trim().isEmpty()) continue;

            Set<String> disparados = new HashSet<>();
            if (c.alertasDisparados != null && !c.alertasDisparados.isEmpty()) {
                disparados.addAll(Arrays.asList(c.alertasDisparados.split(",")));
            }

            long minutosRestantes = Duration.between(now, c.dataHora).toMinutes();

            for (String minStr : c.alertasMinutos.split(",")) {
                minStr = minStr.trim();
                if (minStr.isEmpty() || disparados.contains(minStr)) continue;

                long alvoMin = Long.parseLong(minStr);
                if (minutosRestantes <= alvoMin && minutosRestantes >= -5) {
                    disparados.add(minStr);
                    c.alertasDisparados = String.join(",", disparados);
                    alterado = true;

                    String tempoDesc = formatarTempo(alvoMin);
                    String msg = String.format("Compromisso: %s\nHorário: %s\nLocal: %s\nAviso: %s antes",
                            c.titulo, c.dataHora.format(BR_FMT), (c.local == null || c.local.isEmpty() ? "N/A" : c.local), tempoDesc);

                    if (trayIcon != null) {
                        trayIcon.displayMessage(c.titulo, msg, TrayIcon.MessageType.INFO);
                    }
                    SwingUtilities.invokeLater(() -> {
                        Toolkit.getDefaultToolkit().beep();
                        JOptionPane.showMessageDialog(null, msg, "LEMBRETE: " + c.titulo, JOptionPane.INFORMATION_MESSAGE);
                    });
                }
            }
        }

        if (alterado) {
            salvarCompromissosDisco();
        }
    }

    private String formatarTempo(long min) {
        if (min == 0) return "Na hora";
        if (min < 60) return min + " minutos";
        if (min < 1440) return (min / 60) + " hora(s)";
        return (min / 1440) + " dia(s)";
    }

    private static class Compromisso {
        int id;
        String titulo;
        LocalDateTime dataHora;
        String local;
        String categoria;
        String prioridade;
        String observacoes;
        String alertasMinutos;
        String alertasDisparados;
        boolean concluido;
    }

    private static class NotaItem {
        int id;
        String conteudo;
        boolean concluido;

        NotaItem(int id, String conteudo, boolean concluido) {
            this.id = id;
            this.conteudo = conteudo;
            this.concluido = concluido;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new AgendaApp().setVisible(true);
        });
    }
}
