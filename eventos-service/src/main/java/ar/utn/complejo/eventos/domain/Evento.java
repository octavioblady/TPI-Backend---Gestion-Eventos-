package ar.utn.complejo.eventos.domain;

import ar.utn.complejo.common.error.BusinessException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "evento")
@Getter
@Setter
@NoArgsConstructor
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    /** 'sub' del token del cliente dueño del evento. No existe tabla de usuarios: la identidad vive en Keycloak. */
    @Column(name = "cliente_id", nullable = false, length = 100)
    private String clienteId;

    @Column(name = "creado_por", nullable = false, length = 100)
    private String creadoPor;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column(nullable = false)
    private LocalDateTime fin;

    @Column(nullable = false)
    private int asistentes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoEvento estado = EstadoEvento.SOLICITADO;

    @Column(name = "precio_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal precioTotal;

    @Column(name = "senia_monto", nullable = false, precision = 14, scale = 2)
    private BigDecimal seniaMonto;

    @Column(name = "senia_abonada", nullable = false)
    private boolean seniaAbonada;

    /** Se fija al cancelar según la regla de 48 h. */
    @Column(name = "senia_reembolsable")
    private Boolean seniaReembolsable;

    @Column(name = "codigo_rechazo", length = 60)
    private String codigoRechazo;

    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("instalacionId")
    private List<EventoInstalacion> instalaciones = new ArrayList<>();

    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("servicioId")
    private List<EventoServicio> servicios = new ArrayList<>();

    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<HistorialEstadoEvento> historial = new ArrayList<>();

    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<AdvertenciaMeteorologica> advertencias = new ArrayList<>();

    public boolean usaAireLibre() {
        return instalaciones.stream().anyMatch(EventoInstalacion::isAireLibre);
    }

    /** Única vía para cambiar de estado: valida la transición y deja registro en el historial. */
    public void cambiarEstado(EstadoEvento nuevo, String motivo, String usuario, LocalDateTime ahora) {
        if (!estado.puedePasarA(nuevo)) {
            throw BusinessException.conflicto("TRANSICION_INVALIDA",
                    "No se puede pasar de " + estado + " a " + nuevo);
        }
        HistorialEstadoEvento h = new HistorialEstadoEvento();
        h.setEvento(this);
        h.setEstadoAnterior(estado);
        h.setEstadoNuevo(nuevo);
        h.setMotivo(motivo);
        h.setUsuario(usuario);
        h.setFecha(ahora);
        historial.add(h);
        estado = nuevo;
    }

    public void registrarCreacion(String usuario, LocalDateTime ahora) {
        HistorialEstadoEvento h = new HistorialEstadoEvento();
        h.setEvento(this);
        h.setEstadoNuevo(EstadoEvento.SOLICITADO);
        h.setMotivo("Evento solicitado");
        h.setUsuario(usuario);
        h.setFecha(ahora);
        historial.add(h);
    }
}
