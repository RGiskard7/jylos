package com.example.jylos.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.example.jylos.data.models.ToDoNote;

/**
 * {@link ToDoNote}: los dos constructores y los accesores propios de fecha límite y de
 * completado. Hereda {@code equals}/{@code hashCode}/{@code getPath} de {@code Note}
 * sin sobrescribirlos — ya cubiertos en {@code NoteIdentityTest} y {@code LeafModelTest},
 * no se repiten aquí.
 *
 * <p>Estaba al 0%: es un simple contenedor de estado usado por
 * {@code FrontmatterHandler} y {@code NoteDAOSQLite}, pero nada ejercitaba ni sus
 * constructores ni sus accesores.</p>
 */
class ToDoNoteTest {

    @Test
    void elConstructorCompletoAsignaTodosLosCampos() {
        ToDoNote tarea = new ToDoNote("id-1", "Comprar pan", "cuerpo",
                "2026-01-01", "2026-01-02", "2026-01-10", "2026-01-09");

        assertEquals("id-1", tarea.getId());
        assertEquals("Comprar pan", tarea.getTitle());
        assertEquals("cuerpo", tarea.getContent());
        assertEquals("2026-01-01", tarea.getCreatedDate());
        assertEquals("2026-01-02", tarea.getModifiedDate());
        assertEquals("2026-01-10", tarea.getToDoDue());
        assertEquals("2026-01-09", tarea.getToDoCompleted());
    }

    @Test
    void elConstructorSinDatosDeTareaLosDejaANull() {
        ToDoNote tarea = new ToDoNote("id-1", "Comprar pan", "cuerpo", "2026-01-01", "2026-01-02");

        assertEquals("Comprar pan", tarea.getTitle(), "los campos heredados de Note sí se asignan");
        assertNull(tarea.getToDoDue(), "sin fecha límite, debe quedar sin asignar");
        assertNull(tarea.getToDoCompleted());
    }

    @Test
    void losSettersPropiosSobrescribenFechaLimiteYCompletado() {
        ToDoNote tarea = new ToDoNote("id-1", "Comprar pan", "", "2026-01-01", "2026-01-02");

        tarea.setToDoDue("2026-03-01");
        tarea.setToDoCompleted("2026-02-28");

        assertEquals("2026-03-01", tarea.getToDoDue());
        assertEquals("2026-02-28", tarea.getToDoCompleted());
    }
}
