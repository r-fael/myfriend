# Como o myfriend funciona

Este documento explica o projeto por dentro: quais são as peças, o que acontece a cada quadro
e onde mexer para mudar cada coisa.

Para a versão desenhada, abra [como-funciona.excalidraw](como-funciona.excalidraw) em
<https://excalidraw.com> (menu, Abrir) ou na extensão Excalidraw do VS Code. A mesma imagem
está em [como-funciona.png](como-funciona.png). O diagrama é gerado por
[tools/gerar_diagrama.py](../tools/gerar_diagrama.py); para mudar o desenho, edite o script e
rode `python tools/gerar_diagrama.py`.

## 1. A ideia

O relógio tem uma tela de uns 450 × 450 pontos. O myfriend trata essa tela como uma grade de
**52 × 52 casas** ("pixels gordos"), como um joguinho antigo de LCD. Tudo o que aparece é feito
pintando casas dessa grade: o rosto, o relógio, a loja.

Isso traz três vantagens:

- desenhar algo novo é só escrever uma lista de casas;
- um quadro inteiro são poucas centenas de quadradinhos, então é barato;
- o fundo é preto de verdade, e em tela OLED pixel preto fica desligado.

## 2. As duas camadas

| Camada | Arquivos | O que faz |
|---|---|---|
| Casca Android | `FriendWatchFaceService`, `MainActivity`, `FriendView`, `PetSensors`, `PetNotificationListener`, `ReminderReceiver`, `SetWatchFaceTool` | Liga o bichinho ao relógio: entrega a tela, os toques, os sensores e os alarmes. |
| Motor | `PetEngine`, `PetState`, `PixelGrid`, `PetAccessoryRenderer`, `PetConfig`, `PetClock`, `PetSignals` | O bichinho em si. É Java puro e não sabe se está num relógio ou num PC. |

A regra é: tudo o que é comportamento do bichinho fica no motor. Por isso o simulador de PC
(`desktop-sim/`) consegue rodar o mesmo código: ele só troca a casca Android por imitações
simples (`desktop-sim/shim/`) de `Canvas`, `Paint`, `SharedPreferences` e companhia.

### Cada arquivo do motor

| Arquivo | Papel |
|---|---|
| `PetEngine.java` | O cérebro. Decide o humor, anima, desenha cada quadro e trata toques e coroa. |
| `PetState.java` | A memória: comida, água, felicidade, energia, moedas, itens. Salva e carrega das preferências e aplica a passagem do tempo. |
| `PixelGrid.java` | A tela de 52 × 52: tamanho das casas, paleta de cores, `px()`, linhas, retângulos, ícones 5 × 5 e dígitos 3 × 5. |
| `PetAccessoryRenderer.java` | Os desenhos grandes: chapéus, óculos, coisas de segurar, comidas, bebidas, relógio, anel de bateria. |
| `PetConfig.java` | Todos os números: velocidades, limites, preços, lista de humores e de itens. |
| `PetClock.java` | A hora "de parede". O simulador consegue adiantar o relógio por aqui. |
| `PetSignals.java` | Recados de fora para o motor (hoje: "chegou uma notificação"). |

### Cada arquivo da casca

| Arquivo | Papel |
|---|---|
| `FriendWatchFaceService.java` | O mostrador. Pede um quadro ao motor e agenda o próximo com o atraso que o motor devolveu. Trata a tela desligada (ambient). |
| `MainActivity.java` + `FriendView.java` | O app aberto. É onde a coroa funciona. |
| `PetSensors.java` | Passos e chacoalhar o pulso. Só fica ligado com a tela acesa. |
| `PetNotificationListener.java` | Avisa o motor quando chega uma notificação (ele mostra um envelope). |
| `ReminderReceiver.java` | Lembrete com o app fechado: vibra se ele está com fome ou sede, ou se a bateria está baixa. |
| `SetWatchFaceTool.java` | Ferramenta de linha de comando para escolher o mostrador por adb. |

## 3. Como um desenho vira números

Um ícone pequeno é guardado como 5 números. Cada número é uma linha do desenho em binário:
1 acende a casa, 0 deixa apagada.

```
0 1 0 1 0  = 10
1 1 1 1 1  = 31
1 1 1 1 1  = 31
0 1 1 1 0  = 14
0 0 1 0 0  = 4        SPRITE_HEART = {10, 31, 31, 14, 4}
```

Os dígitos do relógio são feitos igual, só que 3 × 5. Desenhos maiores (chapéus, comidas) são
escritos como comandos dentro do `PetAccessoryRenderer`: "pinte a linha do x 3 ao x 7",
"pinte esta casa de dourado". Tudo é medido a partir do centro do rosto, e o que é simétrico
é desenhado uma vez e espelhado (`mirrorHline`, `mirrorPx`).

## 4. O que acontece em um quadro

`PetEngine.renderScene()` roda estes passos, sempre nesta ordem:

1. Limpa a tela (preto).
2. Passa o tempo: comida, água, felicidade e energia caem um pouquinho.
3. Rotina: é hora de dormir, de bocejar, chegou mensagem?
4. Escolhe o humor (seção 5).
5. Mexe o corpo: olhar, piscada, pulinho com gravidade.
6. Desenha o rosto: bochechas, olhos e boca do humor atual.
7. Veste: pescoço, nariz, óculos, chapéu e o que ele segura.
8. Desenha objetos e partículas: comida, copo, corações, Zzz.
9. Desenha o painel por cima: relógio e botões, ou a loja.
10. Devolve quantos milissegundos esperar até o próximo quadro (seção 8).

As animações dependem do tempo, não do quadro anterior. A mordida na comida, por exemplo, é
calculada por "quanto tempo passou desde o começo da ação". Por isso desenhar menos quadros
não quebra nada: a animação só fica menos lisa.

## 5. Como ele decide o humor

O humor não fica guardado. Ele é recalculado a cada quadro em `getCurrentMood()`, perguntando
de cima para baixo. A primeira resposta "sim" ganha:

1. Tem uma ação em andamento (comendo, carinho, bravo, tonto)? Cada ação tem hora para acabar.
2. Ficou 3 horas sem te ver? Emburrado, de costas.
3. Está dormindo?
4. Bateria do relógio em 15% ou menos?
5. Água abaixo de 35? Com sede.
6. Comida abaixo de 32? Com fome.
7. É dia carente e faz 15 minutos sem carinho?
8. Energia abaixo de 25? Com sono.
9. Felicidade abaixo de 30, ou muita sujeira? Triste.
10. Nada disso: feliz.

A personalidade do dia (normal, preguiçoso, elétrico, carente) é sorteada a partir da data,
então não precisa ser salva.

## 6. O tempo e a memória

As quatro necessidades vão de 0 a 100 e caem por hora (`PetConfig`):

| | comida | água | felicidade | energia |
|---|---|---|---|---|
| acordado | −36 | −45 | −34 | −34 |
| dormindo | −6 | −7 | 0 | +200 |

Com a tela apagada nada roda. Ao apagar, o `PetState.save()` grava tudo junto com a hora
(`savedTime`). Ao acender, o `load()` calcula quanto tempo passou e aplica a queda de uma
vez (`passTime`). O resultado é o mesmo de ter ficado ligado.

Os itens comprados ficam num número inteiro por categoria, um bit por item
(`unlockedHead`, `unlockedFood`...). Comprar é ligar um bit e descontar as moedas.

Quando um item sai do jogo, a chave `itemsVersion` diz ao `load()` quais migrações ainda
faltam rodar, e quem tinha o item recebe as moedas de volta:

| `itemsVersion` | Mudança |
|---|---|
| 2 | cocar de carnaval removido |
| 3 | roda divina removida |
| 4 | cores do rostinho removidas |
| 5 | fundos animados removidos; comidas e bebidas entraram |

## 7. Toques

O motor recebe só a posição do dedo (`handleTouch`) e decide o que foi tocado:

- **Topo**: relógio. Um toque troca para a data.
- **Arco de baixo**: comida, bebida, carinho, dormir, loja. Os ícones ficam a cada 28 graus;
  o motor calcula o ângulo do dedo em relação ao centro e escolhe o ícone mais próximo.
- **Meio**: cutucar. Cinco cutucadas rápidas e ele fica bravo.
- **Arrastar no rosto**: carinho.
- **Coroa**: carinho fora da loja; dentro da loja, troca o item.

O painel aparece ao tocar e some depois de 4,5 segundos. Sozinho, o rosto cresce.

A loja (roupinhas) e a lancheira (comidas e bebidas) são a mesma tela. A diferença é a
categoria aberta: as cinco primeiras (`SHOP_CATEGORY_COUNT`) são roupinhas, as outras duas
são `CATEGORY_FOOD` e `CATEGORY_DRINK`. Não há nenhum texto na tela, só ícones e números.

## 8. Bateria

A regra é só trabalhar quando algo muda na tela. O passo 10 de cada quadro
(`nextFrameDelay`) escolhe o ritmo:

| Situação | Ritmo |
|---|---|
| Tela desligada | 1 quadro por minuto |
| Parado | 1 a 2 quadros por segundo (respirar, piscar) |
| Painel aberto | 2 quadros por segundo |
| Ação sem movimento (bravo, bocejo) | 20 quadros por segundo |
| Comendo ou bebendo | 30 quadros por segundo |
| Pulo, zoom, olhar seguindo o dedo | 60 quadros por segundo |

Outros cuidados:

- sensores só com a tela acesa (`PetSensors.start/stop`);
- nenhum objeto é criado a cada quadro (as formas ficam em constantes);
- o lembrete em segundo plano marca a próxima conferida para quando a fome ou a sede devem
  chegar no limite, entre 30 minutos e 2 horas, e nunca de madrugada;
- nenhuma rede.

### Medidas da versão 1.16

Quadros desenhados no simulador, antes e depois de baixar o ritmo das ações:

| Cena | 1.15 | 1.16 |
|---|---|---|
| Birra de 8 s | 500 | 193 |
| Carinho, 3 s | 116 | 52 |
| Comer, 4 s | 181 | 123 |
| Beber, 4 s | 181 | 122 |
| Cutucada com painel, 5 s | 103 | 90 |
| Parado, 30 s | 52 | 52 |

São contagens de quadros no simulador, não medidas de consumo no relógio.

## 9. Receitas

### Adicionar um chapéu (ou outro item da loja)

1. `PetConfig`: crie a constante (`HEAD_NOVO = 18`) e ponha o preço no fim do bloco da
   categoria em `ITEM_PRICES`.
2. `PetAccessoryRenderer.drawHeadItem`: adicione o `case` com o desenho.
3. Rode o simulador e confira na loja.

Sempre adicione no fim da lista. Os itens são guardados pela posição, então inserir no meio
troca o que as pessoas já compraram.

### Adicionar uma comida ou bebida

1. `PetConfig`: constante, preço e os valores em `FOOD_HUNGER` e `FOOD_HAPPINESS`
   (ou `DRINK_HAPPINESS`).
2. `PetAccessoryRenderer.drawFood` (5 × 5) ou `drawDrinkCup` e `drinkPaint` (cores do líquido).

### Remover um item

Guarde o preço numa constante `REMOVED_...`, suba o `itemsVersion` em `PetState.save()` e
escreva a migração em `load()` devolvendo as moedas.

### Mudar o equilíbrio

Tudo está em `PetConfig`: quanto cada necessidade cai por hora, os limites de fome e sede,
os preços e a duração da birra.

## 10. Rodar, testar e instalar

| Comando | O que faz |
|---|---|
| `desktop-sim\run.bat` | Compila e abre o simulador de PC. |
| `gradlew.bat assembleRelease` | Gera o APK em `app/build/outputs/apk/release/`. |
| `instalar.bat` | Compila, acha o relógio no Wi-Fi e instala por cima. Com `-Mostrador`, abre o seletor de mostrador. |
| `criar-chave.bat` | Cria a chave fixa de assinatura (uma vez só). |

Teclas do simulador: mouse é o toque, rodinha é a coroa, setas para cima e para baixo mudam a
bateria, `C` carregador, `M` troca o humor, `S` +100 passos, `G` +1000 passos, `A` tela
desligada, `W` app ou mostrador, `N` notificação, `D` chacoalhar, `H` +1 hora, `U` +1 dia,
`R` apaga e acende a tela, `0` volta para a hora real.

O relógio só aceita atualizar um app assinado com a mesma chave. Sem o `keystore.properties`
o build usa a chave de debug, que é diferente em cada PC. Com a chave fixa criada pelo
`criar-chave.bat`, a atualização entra por cima de qualquer PC sem apagar o bichinho.
