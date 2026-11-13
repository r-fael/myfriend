"""Gera docs/como-funciona.excalidraw (e uma prévia em PNG) explicando o myfriend do zero.

Uso:  python tools/gerar_diagrama.py
Precisa do Pillow (pip install pillow). As imagens vêm de docs/img.
Abra o .excalidraw em https://excalidraw.com (Abrir) ou na extensão do VS Code.
"""
import base64
import json
import os
import random

from PIL import Image, ImageDraw, ImageFont

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IMG = os.path.join(RAIZ, 'docs', 'img')
SAIDA = os.path.join(RAIZ, 'docs', 'como-funciona.excalidraw')
PREVIA = os.path.join(RAIZ, 'docs', 'como-funciona.png')

W = 1900          # largura da página
M = 50            # margem interna das seções
INK = '#1e1e1e'
GRAY = '#666666'
rnd = random.Random(7)
els = []          # elementos do Excalidraw, na ordem de desenho
files = {}        # imagens embutidas
AGORA = 1763000000000


def fonte(size, bold=False, hand=False):
    nome = 'comicbd.ttf' if hand else ('arialbd.ttf' if bold else 'arial.ttf')
    try:
        return ImageFont.truetype(os.path.join('C:\\Windows\\Fonts', nome), size)
    except OSError:
        return ImageFont.load_default()


def largura(texto, size, hand=False):
    f = fonte(size, hand=hand)
    return max(f.getlength(linha) for linha in texto.split('\n')) * (1.12 if hand else 1.04)


def quebrar(texto, max_w, size, hand=False):
    """Quebra o texto em linhas que cabem em max_w (respeita os \\n que já existem)."""
    f = fonte(size, hand=hand)
    fator = 1.12 if hand else 1.04
    saida = []
    for paragrafo in texto.split('\n'):
        linha = ''
        for palavra in paragrafo.split(' '):
            teste = (linha + ' ' + palavra).strip()
            if f.getlength(teste) * fator <= max_w or not linha:
                linha = teste
            else:
                saida.append(linha)
                linha = palavra
        saida.append(linha)
    return '\n'.join(saida)


def base(tipo, x, y, w, h, **extra):
    el = {
        'id': 'el%04d' % len(els), 'type': tipo, 'x': round(x, 1), 'y': round(y, 1),
        'width': round(w, 1), 'height': round(h, 1), 'angle': 0,
        'strokeColor': INK, 'backgroundColor': 'transparent', 'fillStyle': 'solid',
        'strokeWidth': 2, 'strokeStyle': 'solid', 'roughness': 1, 'opacity': 100,
        'groupIds': [], 'frameId': None, 'roundness': None,
        'seed': rnd.randint(1, 2 ** 31 - 1), 'version': 1, 'versionNonce': rnd.randint(1, 2 ** 31 - 1),
        'isDeleted': False, 'boundElements': None, 'updated': AGORA, 'link': None, 'locked': False,
    }
    el.update(extra)
    els.append(el)
    return el


def R(x, y, w, h, bg='transparent', stroke=INK, redondo=True, sw=2, rough=1, tracejado=False, opacidade=100):
    return base('rectangle', x, y, w, h, backgroundColor=bg, strokeColor=stroke, strokeWidth=sw,
                roughness=rough, roundness={'type': 3} if redondo else None,
                strokeStyle='dashed' if tracejado else 'solid', opacity=opacidade)


def T(x, y, texto, size=20, cor=INK, hand=False, alinhar='left', w=None, max_w=None):
    """Texto; devolve o y logo abaixo dele."""
    if max_w:
        texto = quebrar(texto, max_w, size, hand)
    linhas = texto.count('\n') + 1
    h = linhas * size * 1.25
    base('text', x, y, w if w else largura(texto, size, hand), h, strokeColor=cor,
         text=texto, originalText=texto, fontSize=size, fontFamily=1 if hand else 2,
         textAlign=alinhar, verticalAlign='top', containerId=None, autoResize=True, lineHeight=1.25)
    return y + h


def CAIXA(x, y, w, h, rotulo, bg='#ffffff', size=18, cor=INK, stroke=INK, negrito_1a=False):
    """Retângulo com texto centralizado dentro."""
    R(x, y, w, h, bg=bg, stroke=stroke)
    texto = quebrar(rotulo, w - 20, size)
    th = (texto.count('\n') + 1) * size * 1.25
    T(x, y + (h - th) / 2, texto, size, cor, alinhar='center', w=w)


def SETA(x1, y1, x2, y2, cor=INK, tracejado=False, sw=2, ponta=True):
    base('arrow', x1, y1, abs(x2 - x1), abs(y2 - y1), strokeColor=cor, strokeWidth=sw,
         strokeStyle='dashed' if tracejado else 'solid', roundness={'type': 2},
         points=[[0, 0], [round(x2 - x1, 1), round(y2 - y1, 1)]], lastCommittedPoint=None,
         startBinding=None, endBinding=None, startArrowhead=None,
         endArrowhead='arrow' if ponta else None, elbowed=False)


def IMAGEM(x, y, w, h, nome):
    caminho = os.path.join(IMG, nome)
    fid = 'img_' + nome.replace('.', '_')
    if fid not in files:
        with open(caminho, 'rb') as f:
            dados = base64.b64encode(f.read()).decode('ascii')
        files[fid] = {'mimeType': 'image/png', 'id': fid, 'dataURL': 'data:image/png;base64,' + dados,
                      'created': AGORA, 'lastRetrieved': AGORA}
    base('image', x, y, w, h, strokeColor='transparent', roughness=0, fileId=fid, status='saved',
         scale=[1, 1], crop=None, _arquivo=caminho)


def GRADE(x, y, linhas, cel=30, cor='#e03131', apagado='#2b2b2b', fundo='#000000'):
    """Desenha um sprite (lista de strings de 0/1) como quadradinhos."""
    R(x - 8, y - 8, len(linhas[0]) * cel + 14, len(linhas) * cel + 14, bg=fundo, stroke=fundo, rough=0)
    for r, linha in enumerate(linhas):
        for c, bit in enumerate(linha):
            R(x + c * cel, y + r * cel, cel - 4, cel - 4, bg=cor if bit == '1' else apagado,
              stroke=cor if bit == '1' else apagado, redondo=False, sw=1, rough=0)


def SECAO(y, numero, titulo, cor_fundo, construir):
    """Bloco numerado: desenha o conteúdo e põe um fundo colorido atrás."""
    inicio = len(els)
    T(M, y + 34, ('%d. ' % numero if numero else '') + titulo, 40, INK, hand=True)
    fim_y = construir(y + 110)
    fundo = {
        'id': 'sec%02d' % numero, 'type': 'rectangle', 'x': 0, 'y': y, 'width': W, 'height': fim_y - y + 50,
        'angle': 0, 'strokeColor': '#ced4da', 'backgroundColor': cor_fundo, 'fillStyle': 'solid',
        'strokeWidth': 1, 'strokeStyle': 'solid', 'roughness': 0, 'opacity': 100, 'groupIds': [],
        'frameId': None, 'roundness': {'type': 3}, 'seed': rnd.randint(1, 2 ** 31 - 1), 'version': 1,
        'versionNonce': rnd.randint(1, 2 ** 31 - 1), 'isDeleted': False, 'boundElements': None,
        'updated': AGORA, 'link': None, 'locked': False,
    }
    els.insert(inicio, fundo)
    return fim_y + 50 + 40


# ====================================================================== Conteúdo

def cabecalho(y):
    IMAGEM(M, y, 230, 230, 'idle.png')
    T(M + 270, y + 20, 'myfriend: como funciona, do pixel ao relógio', 54, INK, hand=True)
    T(M + 270, y + 105, 'Um bichinho virtual que mora no mostrador do relógio. Leia de cima para baixo: cada bloco '
      'responde uma pergunta, começando pelo mais básico (como um quadradinho aparece na tela) até o app '
      'chegar no seu pulso.', 24, GRAY, max_w=1500)
    T(M + 270, y + 195, 'Os nomes em azul são arquivos do projeto (app/src/main/java/com/myfriend).', 20, '#1971c2')
    return y + 230


def s1(y):
    T(M, y, 'A tela do relógio tem uns 450 × 450 pontos de verdade. O myfriend finge que ela tem só 52 × 52 '
      '"pixels gordos", como um joguinho antigo de LCD. O rosto, o relógio, a loja: tudo é feito acendendo '
      'esses quadradinhos.', 24, max_w=1080)
    yy = y + 150
    CAIXA(M, yy, 250, 90, 'Tela real\n432 × 432 pontos', '#d0ebff')
    SETA(M + 255, yy + 45, M + 325, yy + 45)
    CAIXA(M + 330, yy, 250, 90, 'dividida em\n52 × 52 casas', '#d0ebff')
    SETA(M + 585, yy + 45, M + 655, yy + 45)
    CAIXA(M + 660, yy, 420, 90, 'cada casa tem 8,3 pontos, mas só\n7,1 são pintados (sobra um vãozinho)', '#d0ebff')
    yy += 130
    T(M, yy, 'PixelGrid.java', 24, '#1971c2')
    T(M, yy + 36, 'É a "tela" do bichinho. Ela sabe três coisas: o tamanho de cada casa, a paleta de cores '
      '(branco, rosa, dourado...) e como pintar uma casa: px(x, y, cor). Linhas, retângulos e ícones são '
      'só vários px() seguidos.', 22, max_w=1080)
    T(M, yy + 160, 'Por que assim?', 24, INK, hand=True)
    T(M, yy + 200, '•  Fica com cara de brinquedo, e qualquer desenho novo é só uma lista de casas.\n'
      '•  É barato: um quadro inteiro são poucas centenas de quadradinhos.\n'
      '•  O fundo é preto de verdade. Em tela OLED, pixel preto fica desligado e não gasta bateria.', 22, max_w=1080)
    # Close do olho.
    IMAGEM(1240, y, 400, 400, 'close.png')
    T(1240, y + 412, 'Um olho visto de perto: cada quadradinho é uma "casa" da grade,\ncom o vãozinho que dá a '
      'cara de LCD.', 20, GRAY)
    SETA(1700, y + 250, 1470, y + 215, '#e03131')
    T(1710, y + 235, 'vãozinho', 20, '#e03131', hand=True)
    return yy + 330


def s2(y):
    T(M, y, 'Um ícone pequeno (coração, lua, raio) é guardado como 5 números. Cada número é uma linha do '
      'desenho escrita em binário: 1 acende a casa, 0 deixa apagada.', 24, max_w=1750)
    yy = y + 100
    linhas = ['01010', '11111', '11111', '01110', '00100']
    valores = [10, 31, 31, 14, 4]
    GRADE(M + 10, yy + 10, linhas, cel=44, cor='#f06595')
    for i, (bits, v) in enumerate(zip(linhas, valores)):
        cy = yy + 10 + i * 44 + 8
        SETA(M + 245, cy + 12, M + 300, cy + 12, GRAY)
        T(M + 315, cy, ' '.join(bits), 24, INK)
        T(M + 470, cy, '=', 24, GRAY)
        T(M + 505, cy, str(v), 24, '#e03131')
    CAIXA(M + 620, yy + 60, 520, 80, 'SPRITE_HEART = {10, 31, 31, 14, 4}', '#fff3bf', 24)
    T(M + 620, yy + 160, 'Para desenhar, o PixelGrid lê cada número bit por bit\ne chama px() onde encontra um 1.', 22, GRAY)
    # Dígito 7.
    GRADE(1330, yy + 10, ['111', '001', '001', '001', '001'], cel=44, cor='#ffffff')
    T(1490, yy + 20, 'Os números do relógio são\nfeitos igual, só que 3 × 5:', 22)
    CAIXA(1490, yy + 95, 300, 60, '7 = {7, 1, 1, 1, 1}', '#fff3bf', 22)
    yy += 270
    T(M, yy, 'E os desenhos grandes?', 26, INK, hand=True)
    T(M, yy + 44, 'Chapéus, comidas e objetos não cabem em 5 × 5, então são desenhados com "comandos": pinte a '
      'linha do x 3 ao x 7, pinte esta casa de dourado... Tudo é medido a partir do centro do rosto, e o que é '
      'simétrico (orelhas, chifres) é desenhado uma vez e espelhado para o outro lado. Isso fica em ', 22, max_w=1750)
    T(M, yy + 134, 'PetAccessoryRenderer.java', 24, '#1971c2')
    T(M + 330, yy + 136, '(roupinhas, comidas, bebidas, relógio, anel de bateria).', 22)
    return yy + 180


def s3(y):
    T(M, y, 'O projeto tem duas camadas. A de cima fala com o Android (tela, toque, sensores). A de baixo é o '
      'bichinho em si, em Java puro: ela não sabe se está num relógio ou num PC.', 24, max_w=1750)
    yy = y + 105
    # Casca Android.
    R(M, yy, 1330, 215, bg='#e7f5ff', stroke='#1971c2', tracejado=True)
    T(M + 20, yy + 12, 'Casca Android (liga o bichinho ao relógio)', 22, '#1971c2', hand=True)
    cascas = [
        ('FriendWatchFaceService', 'o mostrador\ndo relógio'),
        ('MainActivity + FriendView', 'o app aberto\n(usa a coroa)'),
        ('PetSensors', 'passos e\nchacoalhar o pulso'),
        ('PetNotificationListener', 'avisa que chegou\numa mensagem'),
        ('ReminderReceiver', 'lembrete com o\napp fechado'),
    ]
    for i, (nome, desc) in enumerate(cascas):
        x = M + 20 + i * 260
        CAIXA(x, yy + 55, 245, 60, nome, '#ffffff', 16, '#1971c2', '#1971c2')
        T(x, yy + 125, desc, 17, GRAY, alinhar='center', w=245)
    # Simulador.
    R(1420, yy, 430, 215, bg='#fff4e6', stroke='#e8590c', tracejado=True)
    T(1440, yy + 12, 'Simulador de PC (desktop-sim)', 22, '#e8590c', hand=True)
    T(1440, yy + 55, 'Uma janela comum de computador mais "imitações" do Android (Canvas, Paint, '
      'preferências). O motor roda igualzinho, então dá para testar tudo sem o relógio.', 18, max_w=395)
    # Setas para o motor.
    SETA(M + 660, yy + 220, M + 660, yy + 290, '#1971c2')
    T(M + 675, yy + 232, 'toques, sensores, "desenhe um quadro"', 18, '#1971c2')
    SETA(1630, yy + 220, 1380, yy + 325, '#e8590c')
    # Motor.
    my = yy + 295
    R(M + 330, my, 1000, 130, bg='#fff3bf', stroke='#f08c00', sw=4)
    T(M + 330, my + 18, 'PetEngine', 34, '#1971c2', alinhar='center', w=1000)
    T(M + 330, my + 70, 'o cérebro: decide o humor, anima e desenha cada quadro, trata os toques', 20, INK,
      alinhar='center', w=1000)
    # Ajudantes.
    ay = my + 215
    ajud = [
        ('PetState', 'a memória: fome, água, felicidade, energia, moedas e itens. Salva nas preferências do relógio.', '#d3f9d8'),
        ('PixelGrid', 'a tela de 52 × 52: paleta de cores e como pintar casas, linhas e ícones.', '#d3f9d8'),
        ('PetAccessoryRenderer', 'os desenhos grandes: chapéus, comidas, relógio, anel de bateria.', '#d3f9d8'),
        ('PetConfig', 'todos os números num lugar só: preços, velocidades, limites, lista de humores.', '#d3f9d8'),
        ('PetClock e PetSignals', 'a hora do dia e os recados que vêm de fora (ex.: chegou notificação).', '#d3f9d8'),
    ]
    for i, (nome, desc, cor) in enumerate(ajud):
        x = M + i * 362
        R(x, ay, 345, 150, bg=cor, stroke='#2f9e44')
        T(x, ay + 12, nome, 21, '#1971c2', alinhar='center', w=345)
        T(x + 12, ay + 48, quebrar(desc, 320, 17), 17, INK)
        SETA(M + 830, my + 135, x + 172, ay - 6, '#2f9e44')
    T(M, ay + 180, 'Regra de ouro: tudo o que é "bichinho" fica na camada de baixo. Por isso o mesmo código roda no '
      'relógio e no simulador, e por isso dá para testar cada comportamento no PC.', 22, '#2f9e44', max_w=1750)
    return ay + 250


def s4(y):
    T(M, y, 'Animação é só desenhar a tela de novo, várias vezes. Cada desenho é um "quadro". Toda vez que o '
      'relógio pede um quadro, o PetEngine.renderScene() faz estes passos, sempre nesta ordem:', 24, max_w=1750)
    passos = [
        ('1', 'Limpa a tela', 'pinta tudo de preto'),
        ('2', 'Passa o tempo', 'fome, sede, felicidade e energia caem um pouquinho'),
        ('3', 'Rotina', 'é hora de dormir? de bocejar? chegou mensagem?'),
        ('4', 'Escolhe o humor', 'feliz, com fome, bravo... (bloco 5)'),
        ('5', 'Mexe o corpo', 'olhar, piscada, pulinho com gravidade'),
        ('6', 'Desenha o rosto', 'bochechas, olhos e boca do humor atual'),
        ('7', 'Veste', 'chapéu, óculos, nariz, pescoço e o que ele segura'),
        ('8', 'Objetos e partículas', 'comida, copo, corações, Zzz, brilhos'),
        ('9', 'Painel por cima', 'relógio e botões, ou a loja'),
        ('10', 'Agenda o próximo', 'devolve "me chame de novo em X ms" (bloco 9)'),
    ]
    yy = y + 100
    bw, bh, gap = 330, 130, 28
    for i, (n, titulo, desc) in enumerate(passos):
        linha, col = divmod(i, 5)
        x = M + col * (bw + gap)
        by = yy + linha * (bh + 70)
        cor = '#ffe8cc' if i < 5 else '#e5dbff'
        R(x, by, bw, bh, bg=cor, stroke='#495057')
        T(x + 14, by + 10, n, 30, '#e8590c' if i < 5 else '#7048e8', hand=True)
        T(x + 62, by + 14, titulo, 22, INK)
        T(x + 14, by + 56, quebrar(desc, bw - 28, 17), 17, GRAY)
        if col < 4:
            SETA(x + bw + 3, by + bh / 2, x + bw + gap - 3, by + bh / 2, '#495057')
    # Seta da linha 1 para a linha 2.
    SETA(M + 4 * (bw + gap) + bw / 2, yy + bh + 4, M + bw / 2, yy + bh + 64, '#495057', tracejado=True)
    fy = yy + 2 * bh + 70 + 20
    T(M, fy, 'Laranja: ele "pensa" (nada aparece na tela). Roxo: ele desenha, de trás para a frente.', 18, GRAY)
    fy += 50
    IMAGEM(M, fy, 250, 250, 'eating.png')
    T(M + 290, fy + 20, 'Exemplo: comendo um hambúrguer', 26, INK, hand=True)
    T(M + 290, fy + 66, 'O passo 4 respondeu "comendo". O passo 6 desenhou os olhinhos fechados e a boca mastigando. '
      'O passo 8 desenhou o hambúrguer com um pedaço faltando (a mordida depende de quanto tempo passou desde o '
      'começo da ação) e soltou farelinhos, que são partículas. Como nada depende do quadro anterior, pular '
      'quadros não quebra a animação: ela só fica menos lisa.', 22, max_w=1500)
    return fy + 260


def s5(y):
    T(M, y, 'O humor não fica guardado: ele é recalculado a cada quadro, perguntando de cima para baixo. A primeira '
      'resposta "sim" ganha.', 24, max_w=1750)
    regras = [
        ('Tem uma ação em andamento?', 'comendo, bebendo, carinho, bravo, tonto... cada ação tem hora para acabar', '#ffd8a8'),
        ('Ficou 3 horas sem te ver?', 'emburrado, de costas (um toque e ele se vira feliz)', '#ffc9c9'),
        ('Está dormindo?', 'dormindo (de noite ele dorme sozinho)', '#d0bfff'),
        ('Bateria do relógio em 15% ou menos?', 'cara de bateria fraca', '#ffc9c9'),
        ('Água abaixo de 35?', 'com sede', '#a5d8ff'),
        ('Comida abaixo de 32?', 'com fome', '#ffd8a8'),
        ('Dia carente e 15 min sem carinho?', 'pede carinho', '#fcc2d7'),
        ('Energia abaixo de 25?', 'com sono', '#d0bfff'),
        ('Felicidade abaixo de 30, ou sujeira?', 'triste', '#a5d8ff'),
        ('Nada disso', 'feliz', '#b2f2bb'),
    ]
    yy = y + 95
    for i, (pergunta, resposta, cor) in enumerate(regras):
        by = yy + i * 62
        R(M, by, 520, 50, bg=cor, stroke='#495057')
        T(M + 14, by + 12, pergunta, 20, INK)
        SETA(M + 525, by + 25, M + 600, by + 25, '#495057')
        T(M + 615, by + 12, resposta, 20, INK)
    IMAGEM(1380, yy, 220, 220, 'grumpy.png')
    T(1380, yy + 226, 'bravo: 5 cutucadas seguidas', 18, GRAY, alinhar='center', w=220)
    IMAGEM(1630, yy, 220, 220, 'sleeping.png')
    T(1630, yy + 226, 'dormindo', 18, GRAY, alinhar='center', w=220)
    R(1380, yy + 280, 470, 250, bg='#fff9db', stroke='#f08c00')
    T(1400, yy + 294, 'Personalidade do dia', 24, INK, hand=True)
    T(1400, yy + 336, 'A data vira um número que sorteia como ele está hoje: normal, preguiçoso (boceja mais), '
      'elétrico (pula sozinho) ou carente (pede carinho). Como depende só da data, não precisa guardar nada.',
      18, max_w=430)
    return yy + 10 * 62 + 10


def s6(y):
    T(M, y, 'Ele tem quatro necessidades, de 0 a 100. Elas caem com o tempo, e é isso que faz você precisar cuidar '
      'dele. Os números ficam no PetConfig.', 24, max_w=1750)
    yy = y + 100
    cab = ['', 'comida', 'água', 'felicidade', 'energia']
    acordado = ['acordado, por hora', '−36', '−45', '−34', '−34']
    dormindo = ['dormindo, por hora', '−6', '−7', '0', '+200']
    cores = ['#f1f3f5', '#ffd8a8', '#a5d8ff', '#fcc2d7', '#ffec99']
    for r, linha in enumerate([cab, acordado, dormindo]):
        for c, txt in enumerate(linha):
            w = 300 if c == 0 else 160
            x = M + (0 if c == 0 else 300 + (c - 1) * 160)
            R(x, yy + r * 56, w, 56, bg=cores[c] if r == 0 or c == 0 else '#ffffff', stroke='#868e96', redondo=False, sw=1, rough=0)
            T(x, yy + r * 56 + 15, txt, 20, INK, alinhar='center', w=w)
    T(M, yy + 185, 'Acordado, tudo zera em no máximo 3 horas.\nDormindo, quase nada cai e a energia enche em 30 minutos.', 20, GRAY)
    # Fluxo salvar/carregar.
    fx = 1040
    T(fx, yy - 10, 'E com a tela apagada?', 26, INK, hand=True)
    CAIXA(fx, yy + 40, 240, 90, 'Tela apaga:\nsalva tudo e a hora', '#d3f9d8')
    SETA(fx + 245, yy + 85, fx + 295, yy + 85)
    CAIXA(fx + 300, yy + 40, 220, 90, 'Nada roda.\nZero bateria.', '#f1f3f5')
    SETA(fx + 525, yy + 85, fx + 575, yy + 85)
    CAIXA(fx + 580, yy + 40, 230, 90, 'Tela acende:\nquanto tempo passou?', '#d3f9d8')
    T(fx, yy + 150, 'Ao acender, ele calcula "agora menos a hora salva" e aplica a queda de uma vez. Se passaram '
      '2 horas, a comida cai 72 de uma vez só. O resultado é igual ao de ficar ligado o tempo todo, sem gastar '
      'nada no meio.', 20, max_w=810)
    yy += 290
    T(M, yy, 'Como as necessidades sobem', 26, INK, hand=True)
    itens = [
        ('Comer', 'cada comida enche um tanto: maçã +28, hambúrguer +45. Doces dão mais felicidade.', '#ffd8a8'),
        ('Beber', 'qualquer bebida enche a água até 100. As compradas alegram mais.', '#a5d8ff'),
        ('Carinho', 'tocar no coração, arrastar o dedo no rosto ou girar a coroa sobe a felicidade e dá moedas.', '#fcc2d7'),
        ('Dormir', 'tocar na lua. Dormindo a energia enche; de noite ele dorme sozinho.', '#ffec99'),
    ]
    for i, (nome, desc, cor) in enumerate(itens):
        x = M + i * 455
        R(x, yy + 50, 435, 130, bg=cor, stroke='#495057')
        T(x + 14, yy + 60, nome, 22, INK)
        T(x + 14, yy + 94, quebrar(desc, 405, 17), 17, INK)
    return yy + 200


def s7(y):
    T(M, y, 'Não existem botões de verdade: o motor recebe só a posição do dedo e decide o que foi tocado. '
      'Isso está em handleTouch(), no PetEngine.', 24, max_w=1750)
    yy = y + 95
    ix, iy, tam = 700, yy + 30, 500
    IMAGEM(ix, iy, tam, tam, 'hud.png')
    def nota(tx, ty, texto, px, py, lado):
        texto = quebrar(texto, 520, 20)
        T(tx, ty, texto, 20, INK)
        alt = (texto.count('\n') + 1) * 25
        larg = largura(texto, 20)
        if lado == 'esq':
            SETA(tx + larg + 10, ty + alt / 2, px, py, '#e03131')
        else:
            SETA(tx - 10, ty + alt / 2, px, py, '#e03131')
    nota(M, yy + 30, 'Topo: relógio. Um toque troca\npara a data e vice-versa.', ix + 190, iy + 70, 'esq')
    nota(M, yy + 140, 'Barrinhas: comida, água,\nfelicidade e energia.', ix + 175, iy + 118, 'esq')
    nota(M, yy + 250, 'Meio: cutucar. Ele pisca ou se assusta.\nCinco cutucadas rápidas e ele fica bravo.\n'
         'Arrastar o dedo no rosto é carinho.', ix + 215, iy + 270, 'esq')
    nota(M, yy + 420, 'Arco de baixo, da esquerda para a direita:\ncomida, bebida, carinho, dormir e loja.',
         ix + 110, iy + 400, 'esq')
    nota(1290, yy + 30, 'Anel verde: bateria do relógio.\nVai encolhendo; amarelo em 30%,\nvermelho piscando em 15%.',
         ix + 455, iy + 110, 'dir')
    nota(1290, yy + 190, 'Coroa (o botão giratório): fora da loja\né carinho; na loja troca o item.',
         ix + 495, iy + 250, 'dir')
    R(1290, yy + 320, 560, 215, bg='#fff9db', stroke='#f08c00')
    T(1310, yy + 334, 'Como ele sabe qual ícone foi tocado?', 22, INK, hand=True)
    T(1310, yy + 374, 'Os ícones ficam num arco, um a cada 28 graus. O motor calcula o ângulo do dedo em relação ao '
      'centro da tela e escolhe o ícone de ângulo mais próximo. Por isso funciona em tela redonda de qualquer '
      'tamanho.', 18, max_w=520)
    T(M, yy + 560, 'O painel (relógio, barrinhas e botões) aparece ao tocar e some depois de 4,5 segundos. Sozinho, o '
      'rosto cresce e ocupa a tela.', 20, GRAY, max_w=1750)
    return yy + 610


def s8(y):
    T(M, y, 'A loja (roupinhas) e a lancheira (comidas e bebidas) são a mesma tela: setas ou coroa trocam o item, '
      'o botão de baixo mostra o preço ou um "certo" verde. Não tem nenhuma palavra, só ícones e números.',
      24, max_w=1750)
    yy = y + 105
    IMAGEM(M, yy, 300, 300, 'shop.png')
    T(M, yy + 306, 'loja: 5 abas de roupinhas', 18, GRAY, alinhar='center', w=300)
    IMAGEM(M + 340, yy, 300, 300, 'menu.png')
    T(M + 340, yy + 306, 'lancheira: comida e bebida', 18, GRAY, alinhar='center', w=300)
    tx = M + 700
    T(tx, yy, 'Um número guarda tudo o que você comprou', 28, INK, hand=True)
    T(tx, yy + 48, 'Cada categoria tem um número inteiro. Cada bit desse número é um item: 1 = é seu, 0 = ainda não. '
      'Exemplo com os chapéus:', 22, max_w=1090)
    bits = ['1', '1', '0', '0', '0', '1', '0', '0']
    nomes = ['nada', 'gorro', 'bruxa', 'abóbora', 'chifres', 'coroa', 'mago', 'fone']
    for i, (b, nome) in enumerate(zip(bits, nomes)):
        x = tx + i * 112
        R(x, yy + 130, 100, 60, bg='#b2f2bb' if b == '1' else '#f1f3f5', stroke='#495057', redondo=False, rough=0, sw=1)
        T(x, yy + 142, b, 28, INK, alinhar='center', w=100)
        T(x, yy + 198, nome, 17, GRAY, alinhar='center', w=100)
        T(x, yy + 104, 'bit %d' % i, 15, GRAY, alinhar='center', w=100)
    T(tx, yy + 240, 'Esse exemplo é o número 35 (1 + 2 + 32). Comprar a coroa foi só "ligar o bit 5" e descontar 80 '
      'moedas. Guardar assim ocupa quase nada e salvar é instantâneo.', 22, max_w=1090)
    yy += 360
    cards = [
        ('Moedas', 'Vêm de cuidar dele: carinho, dar comida e água, limpar a sujeira, caminhar.', '#fff3bf'),
        ('Comprou, é seu', 'Roupinhas, comidas e bebidas são compradas uma vez. Maçã e água são grátis.', '#d3f9d8'),
        ('Item removido', 'Se um item sai do jogo numa atualização, o PetState devolve as moedas de quem tinha (migração).', '#ffe3e3'),
        ('Onde fica salvo', 'Nas preferências do app (SharedPreferences), que entram no backup do relógio.', '#e7f5ff'),
    ]
    for i, (nome, desc, cor) in enumerate(cards):
        x = M + i * 455
        R(x, yy, 435, 130, bg=cor, stroke='#495057')
        T(x + 14, yy + 10, nome, 22, INK)
        T(x + 14, yy + 44, quebrar(desc, 405, 17), 17, INK)
    return yy + 140


def s9(y):
    T(M, y, 'Relógio tem bateria pequena, então a regra é: só trabalhar quando algo muda na tela. No passo 10 de cada '
      'quadro, o motor diz quanto tempo pode esperar até o próximo.', 24, max_w=1750)
    yy = y + 100
    barras = [
        ('Tela desligada', '1 quadro por minuto (só a hora e o rostinho dormindo, em cinza)', 14, '#ced4da'),
        ('Parado, olhando para você', '1 a 2 quadros por segundo (respirar, piscar)', 60, '#b2f2bb'),
        ('Painel aberto', '2 quadros por segundo (os dois pontinhos do relógio piscam)', 90, '#b2f2bb'),
        ('Ação sem movimento (bravo, bocejo)', '20 quadros por segundo', 230, '#ffec99'),
        ('Comendo ou bebendo', '30 quadros por segundo', 340, '#ffd8a8'),
        ('Pulo, zoom, olhar seguindo o dedo', '60 quadros por segundo, só enquanto dura', 640, '#ffc9c9'),
    ]
    for i, (nome, desc, comp, cor) in enumerate(barras):
        by = yy + i * 58
        T(M, by + 10, nome, 20, INK)
        R(M + 400, by + 4, comp, 38, bg=cor, stroke='#495057', rough=0, sw=1)
        T(M + 400 + comp + 16, by + 12, desc, 18, GRAY)
    IMAGEM(1610, yy - 10, 230, 230, 'ambient.png')
    T(1610, yy + 226, 'tela desligada', 18, GRAY, alinhar='center', w=230)
    yy += 6 * 58 + 30
    T(M, yy, 'Outros cuidados', 26, INK, hand=True)
    cards = [
        ('Fundo preto', 'Em tela OLED, pixel preto fica desligado. Quase toda a tela está apagada.', '#f1f3f5'),
        ('Sensores com hora', 'Passos e acelerômetro só ficam ligados com a tela acesa.', '#d3f9d8'),
        ('Lembrete esperto', 'Com o app fechado, o relógio só acorda quando a fome ou a sede vão chegar no limite (de 30 min a 2 h), e nunca de madrugada.', '#e7f5ff'),
        ('Sem lixo', 'Nenhum objeto novo é criado a cada quadro, então o sistema não precisa parar para limpar memória.', '#fff3bf'),
    ]
    for i, (nome, desc, cor) in enumerate(cards):
        x = M + i * 455
        R(x, yy + 48, 435, 150, bg=cor, stroke='#495057')
        T(x + 14, yy + 58, nome, 22, INK)
        T(x + 14, yy + 92, quebrar(desc, 405, 17), 17, INK)
    yy += 225
    R(M, yy, 1800, 96, bg='#ebfbee', stroke='#2f9e44')
    T(M + 20, yy + 12, 'Medido no simulador (versão 1.16, quadros desenhados): birra de 8 s: de 500 para 193.  '
      'Carinho de 3 s: de 116 para 52.  Comer: de 181 para 123.  Beber: de 181 para 122.\nO ganho veio de não '
      'usar 60 quadros por segundo quando só a pose muda.', 20, '#2b8a3e', max_w=1760)
    return yy + 100


def s10(y):
    T(M, y, 'O mesmo código vira duas coisas: o app do relógio e a janela de teste no PC.', 24, max_w=1750)
    yy = y + 85
    etapas = [
        ('Código Java', 'app/src/main/java', '#e7f5ff'),
        ('Gradle + R8', 'compila e joga fora o que não é usado', '#e7f5ff'),
        ('APK de 40 KB', 'o app inteiro, menor que uma foto', '#d3f9d8'),
        ('Assinatura', 'chave fixa (criar-chave.bat) prova que a atualização é sua', '#fff3bf'),
        ('instalar.bat', 'acha o relógio no Wi-Fi e instala por cima, sem perder o bichinho', '#ffe8cc'),
        ('Relógio', 'escolha o mostrador myfriend', '#fcc2d7'),
    ]
    bw = 270
    for i, (nome, desc, cor) in enumerate(etapas):
        x = M + i * (bw + 36)
        R(x, yy, bw, 130, bg=cor, stroke='#495057')
        T(x, yy + 12, nome, 22, INK, alinhar='center', w=bw)
        T(x + 10, yy + 50, quebrar(desc, bw - 20, 17), 17, GRAY, alinhar='center', w=bw - 20)
        if i < len(etapas) - 1:
            SETA(x + bw + 4, yy + 65, x + bw + 32, yy + 65, '#495057')
    # Ramo do simulador.
    SETA(M + 135, yy + 135, M + 135, yy + 205, '#e8590c', tracejado=True)
    CAIXA(M, yy + 210, 420, 100, 'desktop-sim/run.bat\nmesmo motor numa janela do PC', '#fff4e6', 19, INK, '#e8590c')
    T(M + 450, yy + 215, 'É aqui que cada mudança é testada antes de ir para o relógio: o mouse é o dedo, a rodinha é '
      'a coroa, e teclas adiantam o tempo, simulam passos, notificação e tela desligada.', 20, max_w=1300)
    T(M, yy + 350, 'Para saber mais: docs/ARQUITETURA.md explica cada arquivo; IDEIAS.md lista o que vem depois.', 22, '#1971c2')
    return yy + 390


def montar():
    y = cabecalho(0) + 60
    y = SECAO(y, 1, 'A ideia: uma tela de 52 × 52 pixels gordos', '#f8f9fa', s1)
    y = SECAO(y, 2, 'Como um desenho vira números', '#fff9f0', s2)
    y = SECAO(y, 3, 'As peças do projeto: quem faz o quê', '#f8f9fa', s3)
    y = SECAO(y, 4, 'O que acontece em um quadro', '#fff9f0', s4)
    y = SECAO(y, 5, 'Como ele decide o humor', '#f8f9fa', s5)
    y = SECAO(y, 6, 'O tempo passa, mesmo com a tela apagada', '#fff9f0', s6)
    y = SECAO(y, 7, 'Toques: onde apertar faz o quê', '#f8f9fa', s7)
    y = SECAO(y, 8, 'Loja e lancheira', '#fff9f0', s8)
    y = SECAO(y, 9, 'Por que quase não gasta bateria', '#f8f9fa', s9)
    y = SECAO(y, 10, 'Do código ao pulso', '#fff9f0', s10)
    return y


def salvar():
    limpos = [{k: v for k, v in el.items() if not k.startswith('_')} for el in els]
    doc = {'type': 'excalidraw', 'version': 2, 'source': 'https://excalidraw.com', 'elements': limpos,
           'appState': {'gridSize': None, 'viewBackgroundColor': '#ffffff'}, 'files': files}
    with open(SAIDA, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)


def previa(altura):
    """Desenha os mesmos elementos com o Pillow, para conferir o layout sem abrir o Excalidraw."""
    img = Image.new('RGB', (W, int(altura)), '#ffffff')
    d = ImageDraw.Draw(img)
    for el in els:
        x, y, w, h = el['x'], el['y'], el['width'], el['height']
        if el['type'] == 'rectangle':
            fill = None if el['backgroundColor'] == 'transparent' else el['backgroundColor']
            raio = 10 if el['roundness'] else 0
            d.rounded_rectangle([x, y, x + w, y + h], radius=min(raio, w / 2, h / 2), fill=fill,
                                outline=el['strokeColor'], width=el['strokeWidth'])
        elif el['type'] == 'text':
            f = fonte(el['fontSize'], hand=el['fontFamily'] == 1)
            for i, linha in enumerate(el['text'].split('\n')):
                lw = f.getlength(linha)
                lx = x + (w - lw) / 2 if el['textAlign'] == 'center' else x
                d.text((lx, y + i * el['fontSize'] * 1.25), linha, font=f, fill=el['strokeColor'])
        elif el['type'] == 'arrow':
            (dx, dy) = el['points'][1]
            x2, y2 = x + dx, y + dy
            d.line([x, y, x2, y2], fill=el['strokeColor'], width=el['strokeWidth'])
            if el['endArrowhead']:
                import math
                ang = math.atan2(dy, dx)
                for lado in (2.6, -2.6):
                    d.line([x2, y2, x2 + 14 * math.cos(ang + lado), y2 + 14 * math.sin(ang + lado)],
                           fill=el['strokeColor'], width=el['strokeWidth'])
        elif el['type'] == 'image':
            fig = Image.open(el['_arquivo']).convert('RGBA').resize((int(w), int(h)), Image.NEAREST)
            img.paste(fig, (int(x), int(y)), fig)
    # Paleta reduzida: o PNG fica bem menor e continua legível.
    img.quantize(96, dither=Image.Dither.NONE).save(PREVIA, optimize=True)


if __name__ == '__main__':
    fim = montar()
    salvar()
    previa(fim)
    print('ok: %d elementos, %d imagens, altura %d' % (len(els), len(files), fim))
