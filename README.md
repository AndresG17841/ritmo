# Ritmo

Mi app para organizar el día: sueño, tareas que aprenden cuánto me demoro, imprevistos, hábitos y plata.

- **Celular y navegador:** https://andresg17841.github.io/ritmo/
- **Windows:** Ritmo para Windows (abre avisos encima de todo y se inicia con el PC).
- **Datos:** en mi base de datos de Supabase, protegidos con mi cuenta.

## Cómo está organizado

| Carpeta | Qué hay |
| --- | --- |
| `src/app.html` | La app: pantallas, planificador, finanzas. |
| `src/cloud.js` | Conexión con Supabase (entrar, guardar, sincronizar). |
| `src/alerts.js` | Los avisos que salen solos. |
| `desktop/` | Ritmo para Windows (Electron): bandeja, inicio con Windows, ventanas de aviso. |
| `docs/` | La app ya armada que publica GitHub Pages. No se edita a mano. |
| `build.py` | Arma `docs/` a partir de `src/`. |

Para publicar un cambio: editar `src/`, correr `python3 build.py` y subir.
