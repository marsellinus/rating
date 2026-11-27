(function () {
  const body = document.body;
  const toggle = document.querySelector(".menu-toggle");
  const nav = document.getElementById("primary-nav");

  if (!toggle || !nav) return;

  function setMenu(open) {
    body.classList.toggle("menu-open", open);
    toggle.setAttribute("aria-expanded", open ? "true" : "false");
  }

  toggle.addEventListener("click", function () {
    setMenu(!body.classList.contains("menu-open"));
  });

  nav.querySelectorAll("a").forEach(function (link) {
    link.addEventListener("click", function () { setMenu(false); });
  });

  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape") setMenu(false);
  });

  document.addEventListener("click", function (event) {
    if (!body.classList.contains("menu-open")) return;
    if (nav.contains(event.target) || toggle.contains(event.target)) return;
    setMenu(false);
  });

  window.addEventListener("resize", function () {
    if (window.innerWidth > 900) setMenu(false);
  });
})();
