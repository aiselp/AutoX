"ui";

ui.layout(
    <vertical>
        <canvas id="board" />
    </vertical>
);
function setPaintColor(paint, color) {
    paint.setARGB(
        colors.alpha(color),
        colors.red(color),
        colors.green(color),
        colors.blue(color),
    )
}
const imgs = JSON.parse(files.read("./images.json"))
//蛇的颜色
const SNAKE_COLOR = colors.parseColor("#9EDBB0");
//背景色
const BG_COLOR = colors.parseColor("#ffffff");
//文本颜色
const TEXT_COLOR = colors.parseColor("#03a9f4");
//蛇头图片
const SNAKE_HEAD = images.fromBase64(imgs.SNAKE_HEAD);
//苹果图片
const APPLE = images.fromBase64(imgs.APPLE);
//围墙方块图片
const WALL_BLOCK = images.fromBase64(imgs.WALL_BLOCK);

events.on('exit', () => {
    SNAKE_COLOR.recyle()
    APPLE.recyle()
    WALL_BLOCK.recyle()
})

//方块宽度
const BLOCK_WIDTH = 40;
//游戏区域宽高
const GAME_BOARD_HEIGHT = 20;
const GAME_BOARD_WIDTH = 15;

//蛇的四个移动方向
const DIRECTION_LEFT = { x: -1, y: 0, angle: 180 };
const DIRECTION_RIGHT = { x: 1, y: 0, angle: 0 };
const DIRECTION_UP = { x: 0, y: -1, angle: 270 };
const DIRECTION_DOWN = { x: 0, y: 1, angle: 90 };

//蛇，是一个蛇身的坐标的数组
var snake = [{ x: 4, y: 2 }, { x: 3, y: 2 }, { x: 2, y: 2 }];
//苹果的坐标
var apple = generateApple();
//当前蛇的移动方向
var direction = DIRECTION_RIGHT;
//标记游戏是否结束
var isGameOver = false;
//蛇自动移动的时间间隔，调小可以增加难度
var MOVE_INTERVAL = 500;
//分数
var score = 0;
//难度
var level = 5;

var game = null;

dialogs.input("请输入难度(1~10)", level)
    .then(function (l) {
        level = Math.max(1, Math.min(10, l));
        MOVE_INTERVAL = 1000 - level * 100;
        //每隔一段时间让蛇自动前进
        game = setInterval(() => {
            move(direction.x, direction.y);
            ui.board.updateCanvas()
        }, MOVE_INTERVAL);
        toast("通过重力感应来改变蛇的移动方向");
    });


function move(dx, dy) {
    log("move: %d, %d", dx, dy);
    direction.x = dx;
    direction.y = dy;
    //蛇前进时把一个新的方块添加到蛇头前面
    var head = snake[0];
    snake.splice(0, 0, {
        x: head.x + dx,
        y: head.y + dy
    });
    //如果蛇头吃到了苹果
    if (snakeEatsApple()) {
        //添加分数和重新生成苹果
        score += 5;
        apple = generateApple();
    } else {
        //没有吃到苹果的情况下把蛇尾去掉保持蛇身长度不变
        snake.pop();
    }
    //碰撞检测
    collisionTest();
}

function snakeEatsApple() {
    return snake[0].x == apple.x && snake[0].y == apple.y;
}

function generateApple() {
    //循环生成苹果直至苹果不会生成在蛇身上
    var x, y;
    do {
        x = random(1, GAME_BOARD_WIDTH);
        y = random(1, GAME_BOARD_HEIGHT);
    } while (!isAppleValid(x, y));
    return { x: x, y: y };
}

function isAppleValid(x, y) {
    for (var i = 0; i < snake.length; i++) {
        if (snake[i].x == x && snake[i].y == y) {
            return false;
        }
    }
    return true;
}

function collisionTest() {
    //检测蛇有没有撞到墙上
    var head = snake[0];
    if (head.x < 1 || head.x > GAME_BOARD_WIDTH
        || head.y < 1 || head.y > GAME_BOARD_HEIGHT) {
        gameOver();
        return;
    }
    //检测蛇有没有撞到自己
    for (var i = 1; i < snake.length; i++) {
        if (equals(snake[i], head)) {
            gameOver();
            return;
        }
    }
}

function gameOver() {
    clearInterval(game)
    isGameOver = true;
}

//监听重力传感器实现重力改变蛇的移动方向
sensors.register("gravity", sensors.delay.game)
    .on("change", function (event, gx, gy, gz) {
        if (Math.abs(gx) > Math.abs(gy) && Math.abs(gx) > 1.8) {
            setDirection(gx > 0 ? DIRECTION_LEFT : DIRECTION_RIGHT);
        }
        if (Math.abs(gy) > Math.abs(gx) && Math.abs(gy) > 1.8) {
            setDirection(gy > 0 ? DIRECTION_DOWN : DIRECTION_UP);
        }
    });

function setDirection(d) {
    if (equals(d, DIRECTION_LEFT) && equals(direction, DIRECTION_RIGHT))
        return;
    if (equals(d, DIRECTION_RIGHT) && equals(direction, DIRECTION_LEFT))
        return;
    if (equals(d, DIRECTION_DOWN) && equals(direction, DIRECTION_UP))
        return;
    if (equals(d, DIRECTION_UP) && equals(direction, DIRECTION_DOWN))
        return;
    direction = d;
}

var paint = new Paint();
ui.board.on("draw", function (canvas) {
    //绘制背景色
    canvas.drawColor(BG_COLOR);
    //绘制分数等信息
    setPaintColor(paint, TEXT_COLOR)
    paint.setTextSize(50);
    canvas.drawText("分数: " + score, 30, 70, paint);
    canvas.drawText("难度: " + level, 400, 70, paint);
    //如果游戏结束则绘制游戏结束字样
    if (isGameOver) {
        canvas.drawText("游戏结束！", canvas.getWidth() - 280, 70, paint);
    }
    //计算坐标偏移，是的游戏区域绘制在画面的水平居中位置
    var offset = {
        x: (canvas.getWidth() - (GAME_BOARD_WIDTH + 2) * BLOCK_WIDTH) / 2,
        y: 100
    };
    //偏移坐标
    canvas.translate(offset.x, offset.y);
    //绘制围墙
    drawWall(canvas, paint);
    //绘制蛇头
    drawSnakeHead(canvas, paint);
    //绘制蛇身
    drawSnakeBody(canvas, paint);
    //绘制苹果
    canvas.drawImage(APPLE, apple.x * BLOCK_WIDTH, apple.y * BLOCK_WIDTH, paint);
});

function drawWall(canvas, paint) {
    for (var i = 0; i <= GAME_BOARD_WIDTH + 1; i++) {
        //上围墙
        drawBlock(canvas, paint, i, 0);
        //下围墙
        drawBlock(canvas, paint, i, GAME_BOARD_HEIGHT + 1);
    }
    for (var i = 0; i <= GAME_BOARD_HEIGHT + 1; i++) {
        //左围墙
        drawBlock(canvas, paint, 0, i);
        //右围墙
        drawBlock(canvas, paint, GAME_BOARD_WIDTH + 1, i);
    }
}

function drawBlock(canvas, paint, x, y) {
    x *= BLOCK_WIDTH;
    y *= BLOCK_WIDTH;
    canvas.drawImage(WALL_BLOCK, x, y, paint);
}

function drawSnakeHead(canvas, paint) {
    var x = snake[0].x * BLOCK_WIDTH;
    var y = snake[0].y * BLOCK_WIDTH;
    var matrix = new android.graphics.Matrix();
    matrix.postRotate(direction.angle, SNAKE_HEAD.getWidth() / 2, SNAKE_HEAD.getHeight() / 2);
    matrix.postTranslate(x, y);
    canvas.drawImage(SNAKE_HEAD, matrix, paint);
}


function drawSnakeBody(canvas, paint) {
    setPaintColor(paint, SNAKE_COLOR)
    for (var i = 1; i < snake.length; i++) {
        var x = snake[i].x * BLOCK_WIDTH;
        var y = snake[i].y * BLOCK_WIDTH;
        canvas.drawCircle(x + BLOCK_WIDTH / 2, y + BLOCK_WIDTH / 2, BLOCK_WIDTH / 2, paint);
    }
}

function equals(point1, point2) {
    return point1.x == point2.x && point1.y == point2.y;
}