package id.casan.app

import androidx.annotation.DrawableRes

/** Satu brand di bar filter bawah. [name] harus sama dengan teks brand di dialog filter situs. */
data class Brand(val name: String, @DrawableRes val logo: Int)

/** Daftar brand. Tambah baris baru di sini (dan logo PNG di res/drawable-nodpi) bila ada brand lain. */
val BRANDS = listOf(
    Brand("Maka", R.drawable.brand_maka),
    Brand("Polytron", R.drawable.brand_polytron),
    Brand("EMOA", R.drawable.brand_emoa),
)

/**
 * Menggerakkan filter bawaan situs casan.id dari luar: membuka dialog filter,
 * mencentang/menghapus centang brand yang dipilih, lalu menekan "Terapkan".
 * Dengan begitu hasil filter sama persis dengan filter asli situs.
 */
object BrandFilterScript {

    private fun jsArray(items: Collection<String>) =
        items.joinToString(prefix = "[", postfix = "]") { "\"" + it.replace("\"", "") + "\"" }

    fun build(selected: Set<String>): String = """
(function(all, wanted) {
  function txt(e) { return (e.textContent || '').trim().toLowerCase(); }
  function shown(e) { var r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; }

  function applyBtn() {
    var c = Array.prototype.slice.call(
      document.querySelectorAll('button,[role=button],div,span,a'))
      .filter(function(e) { return txt(e) === 'terapkan' && shown(e); });
    return c.length ? c[c.length - 1] : null;
  }

  function filterBtn() {
    var inp = document.querySelector('input[placeholder*="Cari lokasi" i]');
    if (!inp) return null;
    var box = inp.parentElement;
    for (var i = 0; i < 4 && box; i++, box = box.parentElement) {
      var c = box.querySelectorAll('button,[role=button],svg');
      if (c.length) {
        var last = c[c.length - 1];
        return last.tagName.toLowerCase() === 'svg'
          ? (last.closest('button,[role=button],div') || last) : last;
      }
    }
    return null;
  }

  function findRow(name) {
    var n = name.toLowerCase();
    var els = Array.prototype.slice.call(document.querySelectorAll('label,div,span,p'))
      .filter(function(e) { return txt(e) === n; });
    for (var k = 0; k < els.length; k++) {
      var row = els[k];
      for (var i = 0; i < 5 && row; i++, row = row.parentElement) {
        var cb = row.querySelector('input[type=checkbox]');
        if (cb) return { row: row, cb: cb };
      }
      return { row: els[k], cb: null };
    }
    return null;
  }

  var opened = !!applyBtn();
  if (!opened) {
    var fb = filterBtn();
    if (!fb) return 'nofilter';
    fb.click();
  }

  window.__casanSel = window.__casanSel || [];
  setTimeout(function() {
    all.forEach(function(name) {
      var hit = findRow(name);
      if (!hit) return;
      var want = wanted.indexOf(name) >= 0;
      var now = hit.cb ? hit.cb.checked : window.__casanSel.indexOf(name) >= 0;
      if (now !== want) (hit.cb || hit.row).click();
      var i = window.__casanSel.indexOf(name);
      if (want && i < 0) window.__casanSel.push(name);
      if (!want && i >= 0) window.__casanSel.splice(i, 1);
    });
    setTimeout(function() {
      var ap = applyBtn();
      if (ap) ap.click();
    }, 200);
  }, opened ? 0 : 350);
  return 'ok';
})(${jsArray(BRANDS.map { it.name })}, ${jsArray(selected)});
""".trimIndent()
}
