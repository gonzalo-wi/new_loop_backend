---
name: loop-observability
description: Agrega o modifica métricas de observabilidad (performance, errores, negocio) en el backend Java de Loop, dejándolas listas para verse en Grafana vía Prometheus. Usar cuando el usuario pida instrumentar métricas, monitorear rendimiento/errores, o preparar el backend para Grafana/Prometheus.
argument-hint: "[qué querés medir, ej: 'errores por endpoint' o 'controles corregidos por Aguas']"
metadata:
  author: loop
  version: "1.0.0"
---

# Loop Observability

Orquesta el agregado de métricas de observabilidad en el backend Java de
Loop, siguiendo el mismo pipeline que ya define `CLAUDE.md` para cambios de
backend, pero con el agente especializado en observabilidad en vez del
genérico.

## Cuándo usar este skill

- El usuario pide agregar/modificar métricas (Micrometer/Actuator).
- El usuario pide poder ver algo en Grafana (rendimiento, errores, tasa de
  requests, una métrica de negocio puntual).
- El usuario pide preparar el backend para monitoreo/observabilidad en
  general.

Si NO es un cambio de observabilidad (es una funcionalidad de negocio
normal), no uses este skill — seguí el pipeline estándar de `CLAUDE.md` con
`loop-java-dev`.

## Paso 0 — Aclarar el alcance si hace falta

Si el pedido es vago ("quiero métricas de todo"), preguntá antes de arrancar
qué es prioritario: ¿rendimiento general (latencia/throughput), errores, una
métrica de negocio específica, o todo lo anterior? Instrumentar de más
genera ruido y cardinalidad innecesaria en Prometheus — mejor acotar.

## Paso 1 — Implementación

Invocá el subagente `loop-java-observability` con el pedido concreto
(qué métricas, qué endpoints/flujos cubrir). Este agente:
- Agrega Actuator + Micrometer si no están.
- Instrumenta lo pedido respetando las convenciones del proyecto.
- Prepara/actualiza el scrape config de Prometheus y un dashboard base de
  Grafana.

## Paso 2 — Testing

Invocá `loop-java-tester` para validar con tests reales (no solo que
compile) que la instrumentación no rompió nada y que, donde corresponda,
los contadores/timers se disparan como se espera.

## Paso 3 — Review

Invocá `loop-java-reviewer` para el veredicto final (bloqueantes /
sugerencias / ok para commitear). Prestá atención en especial a:
- Que los endpoints de `/actuator/**` queden protegidos correctamente
  (salvo `/actuator/health`).
- Que no haya tags de alta cardinalidad (IDs, emails, valores libres).
- Que no se dupliquen métricas que Spring Boot/Micrometer ya exponen solas.

Si hay bloqueantes: volvé al Paso 1 con `loop-java-observability` para
corregir, y repetí Testing y Review — no saltear el ciclo.

## Paso 4 — Resumen y verificación

Si el review dio OK, en la conversación principal (no delegues esto a un
subagente) mostrale al usuario:
- Qué métricas se agregaron y qué responden (performance, errores, negocio).
- Resultado de los tests.
- Cómo verlas en la práctica: endpoint `/actuator/prometheus`, y si se
  agregó/actualizó un compose de observabilidad, cómo levantarlo
  (`docker compose -f observability/docker-compose.observability.yml up`)
  e importar el dashboard de Grafana generado.
- Un mensaje de commit propuesto.

Esperá confirmación explícita del usuario en el chat antes de correr
`git add` / `git commit` — igual que en el pipeline estándar de
`CLAUDE.md`. Nunca commitear solo porque el review salió limpio.

## Paso 5 — Changelog

Después del commit (con aprobación del usuario), agregá una entrada en
`CHANGELOG.md` bajo `[Unreleased]`, en la categoría que corresponda (casi
siempre `Added` o `Changed`), describiendo en una frase entendible qué se
puede monitorear ahora y por qué — no el mensaje de commit textual.
