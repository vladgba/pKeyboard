<?php
// VKeyboard Layouts & Themes Browser Installer (Markdown-style PHP page)
// Serves .txt layouts and .ini themes with vkeyboard://install deep links.

$dir = __DIR__ . '/../examples/backup-example/layouts';
$files = [];
if (is_dir($dir)) {
    foreach (scandir($dir) as $f) {
        if ($f === '.' || $f === '..') continue;
        $ext = pathinfo($f, PATHINFO_EXTENSION);
        if ($ext === 'txt' || $ext === 'ini') {
            $files[] = $f;
        }
    }
    sort($files, SORT_NATURAL | SORT_FLAG_CASE);
}

function b64url(string $data): string {
    return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
}
?>
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>VKeyboard Layouts</title>
<style>
  body {
    max-width: 760px;
    margin: 2rem auto;
    padding: 0 1rem;
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    line-height: 1.6;
    color: #1f2328;
    background: #ffffff;
  }
  @media (prefers-color-scheme: dark) {
    body { color: #e6edf3; background: #0d1117; }
    a { color: #58a6ff; }
    .card { border-color: #30363d; }
    code { background: #161b22; }
  }
  h1, h2 { border-bottom: 1px solid #d0d7de; padding-bottom: 0.3rem; }
  .card {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.5rem 0.75rem;
    margin: 0.4rem 0;
    border: 1px solid #d0d7de;
    border-radius: 6px;
  }
  code { background: #f6f8fa; padding: 0.15rem 0.35rem; border-radius: 4px; }
  a.btn {
    text-decoration: none;
    padding: 0.25rem 0.6rem;
    border: 1px solid currentColor;
    border-radius: 4px;
    font-weight: 600;
  }
</style>
</head>
<body>
<h1># VKeyboard Layouts</h1>
<p>Tap <code>Install</code> in your phone browser to import directly into VKeyboard without internet permission.</p>

<h2>## Available Files (<?= count($files) ?>)</h2>
<?php if (empty($files)): ?>
<p>No <code>.txt</code> or <code>.ini</code> files found in <code><?= htmlspecialchars($dir) ?></code>.</p>
<?php else: ?>
<?php foreach ($files as $file):
    $path = $dir . '/' . $file;
    $content = file_get_contents($path);
    $name = pathinfo($file, PATHINFO_FILENAME);
    $ext = pathinfo($file, PATHINFO_EXTENSION);
    $type = ($ext === 'ini') ? 'theme' : 'layout';
    $link = 'vkeyboard://install?name=' . rawurlencode($name)
          . '&type=' . rawurlencode($type)
          . '&data=' . b64url($content);
?>
<div class="card">
  <span><code><?= htmlspecialchars($file) ?></code> (<?= htmlspecialchars($type) ?>)</span>
  <a class="btn" href="<?= htmlspecialchars($link) ?>">Install</a>
</div>
<?php endforeach; ?>
<?php endif; ?>
</body>
</html>
