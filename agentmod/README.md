# Agent (Fabric 1.21.1)

## Gerar o .jar
Requer JDK 21. Dentro desta pasta:
    gradle wrapper --gradle-version 8.10   (uma vez; ou copie o gradlew de um projeto Fabric)
    ./gradlew build                        (Windows: gradlew.bat build)
O mod fica em build/libs/agent-1.0.0.jar (NÃO use o -sources.jar).
Coloque em .minecraft/mods junto com o Fabric API.

## Uso
- Ovo de spawn do Agente (aba Spawn Eggs). Clique direito vazio = vincula a você.
- Clique direito de mão vazia: ele entrega os itens. Agachado + mão vazia: troca modo (follow/stay/collect).
- Clique direito com item: ele guarda.
- Varinha de Caminho: clique em blocos para marcar pontos, clique no Agente para patrulhar. Agachar + usar no ar limpa.
- /agent mode follow|stay|collect
- /agent fetch <item> [qtd]   (acha o baú mais próximo com o item, pega e traz até você)
- /agent give
- Redstone (qualquer fonte ligada em ~3 blocos) = velocidade normal. Sem ela: lento por 5s e senta no teto mais próximo.
