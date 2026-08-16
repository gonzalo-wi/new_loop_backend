Loop — Reglas del proyecto

Backend Java de gestión y control de mercadería. Este archivo se carga automáticamente en cada sesión y en cada subagente — acá van las reglas que aplican siempre, sin importar quién esté trabajando.

Estándares de código (aplican a todo el código Java del proyecto)
Seguir la arquitectura ya existente en el repo. No introducir un patrón o capa nueva sin que se pida explícitamente.
Nada de valores mágicos: usar constantes, enums o config.
Principios SOLID.
Código limpio: métodos cortos, nombres descriptivos, responsabilidad única por clase.
Excepciones tipadas del dominio, no genéricas.
Pensar en escalabilidad: evitar acoplar lógica de negocio a detalles de infraestructura.
Flujo de trabajo para nuevas funcionalidades o cambios

Cuando se pida implementar algo en el backend Java, seguir este pipeline en orden, usando los subagentes correspondientes:

loop-java-dev — implementa la funcionalidad.
loop-java-tester — escribe y corre los tests, valida que todo pase en verde.
loop-java-reviewer — revisa el código ya testeado y da el veredicto (bloqueantes / sugerencias / ok para commitear).
Solo si el reviewer dio OK: mostrarle al usuario en la conversación principal un resumen de qué se hizo, el resultado de los tests, y un mensaje de commit propuesto. Esperar confirmación explícita del usuario en el chat antes de correr git add / git commit. Nunca commitear sin esa aprobación explícita, incluso si el review salió limpio.
Después del commit (o al cerrar la tarea), agregar una entrada en CHANGELOG.md describiendo el cambio (ver formato abajo).

Si el reviewer marca bloqueantes, volver a loop-java-dev para corregir y repetir el ciclo desde el paso 2 — no saltear el testing después de una corrección.

Changelog

Cada funcionalidad, fix o cambio relevante que se commitea debe sumar una entrada en CHANGELOG.md, sección [Unreleased], categorizada en Added / Changed / Fixed / Removed, con una descripción breve y clara (no el mensaje de commit textual, una frase entendible para alguien que no vio el código).