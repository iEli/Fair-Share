<template>
  <main class="page-shell">
    <section class="hero" aria-labelledby="hero-title">
      <div class="hero-shade" aria-hidden="true"></div>

      <div class="hero-content">
        <div class="brand-copy">
          <a class="wordmark" href="#home" aria-label="FairShare home">
            <span class="wordmark-mark" aria-hidden="true">f</span>
            <span>fairshare</span>
          </a>
          <p class="eyebrow">Roommates, on the same page</p>
          <h1 id="hero-title">Less struggle.<br /><span>More saving.</span></h1>
          <p class="hero-description">Shared living is better when the little things are sorted.</p>
        </div>

        <nav class="auth-actions" aria-label="Account actions">
          <a class="btn btn-primary" href="/login.html">Log in <span aria-hidden="true">↗</span></a>
          <a class="btn btn-secondary" href="/signup.html">Sign up</a>
        </nav>
      </div>

      <figure class="testimonial">
        <blockquote>“Such a lifesaver! Roommates were such a headache before <em>FairShare</em>!”</blockquote>
        <figcaption>Kyle Rockland <span>·</span> Student and (happy) roommate</figcaption>
      </figure>

      <a class="scroll-cue" href="#about" aria-label="Scroll to learn about FairShare">
        <span aria-hidden="true">↓</span>
      </a>
    </section>

    <section id="about" class="footer-info" aria-label="About FairShare">
      <article>
        <p class="section-index">01 / The idea</p>
        <h2>About us</h2>
        <p>We are five college kids looking to create a helpful tool for people living with roommates. Our goal is to help people track shared expenses so everybody can pay their FairShare.</p>
      </article>

      <article>
        <p class="section-index">02 / The beginning</p>
        <h2>How we got started</h2>
        <p>This project started as an idea for CSCI-4440 Software Design and Documentation at Rensselaer Polytechnic Institute. Tasked with creating a semester-long project, we got to work!</p>
      </article>

      <article>
        <p class="section-index">03 / Say hello</p>
        <h2>Contact us</h2>
        <p>Please don't.</p>
      </article>
    </section>

    <footer class="site-footer">
      <a class="footer-brand" href="#home">fairshare</a>
      <span>Better together, down to the last dollar.</span>
      <span>© FairShare 2026</span>
    </footer>
  </main>
</template>

<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Manrope:wght@400;500;600;700;800&display=swap');

:root {
  font-family: 'DM Sans', sans-serif;
  color: #20251f;
  background: #f4f3ed;
  font-synthesis: none;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  font-optical-sizing: auto;
  font-weight: 400;
  --ink: #20251f;
  --paper: #f4f3ed;
  --lime: #d7f36a;
}

*,
*::before,
*::after {
  box-sizing: border-box;
}

html {
  min-width: 320px;
  scroll-behavior: smooth;
}

body {
  min-width: 320px;
  min-height: 100vh;
  margin: 0;
}

body,
a {
  color: inherit;
}

#app {
  display: block;
  width: 100%;
  max-width: none;
  margin: 0;
  padding: 0;
}

a {
  text-decoration: none;
}

.page-shell {
  width: 100%;
  overflow: hidden;
}

.hero {
  position: relative;
  display: flex;
  min-height: min(790px, 90svh);
  min-height: min(790px, 90vh);
  flex-direction: column;
  justify-content: space-between;
  padding: clamp(28px, 5.4vw, 78px) clamp(24px, 8.2vw, 124px) 46px;
  overflow: hidden;
  color: #fff;
  isolation: isolate;
  background-color: #384138;
  background-image: url('https://images.unsplash.com/photo-1529156069898-49953e39b3ac?auto=format&fit=crop&w=2200&q=85');
  background-position: center 46%;
  background-size: cover;
}

.hero-shade {
  position: absolute;
  z-index: -1;
  inset: 0;
  background: linear-gradient(90deg, rgb(16 25 20 / 75%) 0%, rgb(17 25 21 / 48%) 42%, rgb(17 25 21 / 12%) 100%), linear-gradient(0deg, rgb(12 17 14 / 52%) 0%, transparent 42%);
}

.hero-content {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 40px;
  animation: enter 750ms ease-out both;
}

.brand-copy {
  max-width: 670px;
}

.wordmark,
.footer-brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-family: Manrope, sans-serif;
  font-size: 19px;
  font-weight: 800;
  letter-spacing: 0;
}

.wordmark-mark {
  display: grid;
  width: 31px;
  height: 31px;
  place-items: center;
  border-radius: 50%;
  color: var(--ink);
  background: var(--lime);
  font-size: 22px;
  line-height: 1;
}

.eyebrow,
.section-index {
  margin: 0;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.4px;
  text-transform: uppercase;
}

.eyebrow {
  margin-top: clamp(76px, 11vh, 122px);
  color: var(--lime);
}

h1,
h2,
p,
blockquote,
figure {
  margin-top: 0;
}

h1 {
  margin-bottom: 18px;
  font-family: Manrope, sans-serif;
  font-size: clamp(54px, 7.2vw, 102px);
  font-weight: 700;
  letter-spacing: 0;
  line-height: 0.99;
}

h1 span {
  color: var(--lime);
}

.hero-description {
  margin-bottom: 0;
  color: rgb(255 255 255 / 82%);
  font-size: 16px;
}

.auth-actions {
  display: flex;
  align-items: center;
  gap: 11px;
  padding-top: 1px;
}

.btn {
  display: inline-flex;
  min-height: 47px;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 0 20px;
  border: 1px solid rgb(255 255 255 / 62%);
  border-radius: 3px;
  font-size: 13px;
  font-weight: 700;
  transition: background-color 160ms ease, color 160ms ease, transform 160ms ease;
}

.btn:hover {
  transform: translateY(-2px);
}

.btn-primary {
  border-color: var(--lime);
  color: var(--ink);
  background: var(--lime);
}

.btn-primary:hover {
  background: #e3ff8a;
}

.btn-secondary:hover {
  color: var(--ink);
  background: #fff;
}

.testimonial {
  position: relative;
  max-width: 500px;
  margin: 64px 0 0 auto;
  padding-left: 20px;
  border-left: 2px solid var(--lime);
  animation: enter 850ms 120ms ease-out both;
}

.testimonial blockquote {
  margin-bottom: 12px;
  font-family: Manrope, sans-serif;
  font-size: clamp(18px, 2vw, 24px);
  font-weight: 500;
  line-height: 1.4;
}

.testimonial em {
  color: var(--lime);
  font-style: normal;
  font-weight: 700;
}

.testimonial figcaption {
  color: rgb(255 255 255 / 73%);
  font-size: 12px;
}

.testimonial figcaption span {
  padding: 0 5px;
  color: var(--lime);
}

.scroll-cue {
  position: absolute;
  bottom: 42px;
  left: clamp(24px, 8.2vw, 124px);
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border: 1px solid rgb(255 255 255 / 52%);
  border-radius: 50%;
  font-size: 20px;
  transition: color 160ms ease, background-color 160ms ease;
}

.scroll-cue:hover {
  color: var(--ink);
  background: #fff;
}

.footer-info {
  display: grid;
  grid-template-columns: 1fr 1fr 0.72fr;
  gap: clamp(32px, 6vw, 94px);
  padding: 74px clamp(24px, 8.2vw, 124px) 70px;
  background: var(--paper);
}

.footer-info article {
  max-width: 370px;
}

.section-index {
  margin-bottom: 18px;
  color: #718046;
}

.footer-info h2 {
  margin-bottom: 13px;
  font-family: Manrope, sans-serif;
  font-size: 23px;
  font-weight: 700;
  letter-spacing: 0;
}

.footer-info article > p:last-child {
  margin-bottom: 0;
  color: #62665d;
  font-size: 14px;
  line-height: 1.8;
}

.site-footer {
  display: flex;
  min-height: 74px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 14px clamp(24px, 8.2vw, 124px);
  border-top: 1px solid #dedfd5;
  color: #74776d;
  background: var(--paper);
  font-size: 11px;
}

.footer-brand {
  color: var(--ink);
  font-size: 16px;
}

@keyframes enter {
  from { opacity: 0; transform: translateY(14px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 760px) {
  .hero {
    min-height: 700px;
    min-height: 90svh;
    padding: 26px 24px 108px;
    background-position: 57% center;
  }

  .hero-shade {
    background: linear-gradient(90deg, rgb(16 25 20 / 76%) 0%, rgb(17 25 21 / 40%) 100%), linear-gradient(0deg, rgb(12 17 14 / 65%) 0%, transparent 65%);
  }

  .hero-content {
    flex-direction: column;
    gap: 27px;
  }

  .eyebrow {
    margin-top: clamp(68px, 11vh, 100px);
  }

  .auth-actions {
    padding-top: 0;
  }

  .testimonial {
    align-self: flex-end;
    margin-top: 44px;
    margin-right: 0;
  }

  .scroll-cue {
    bottom: 28px;
    left: 24px;
  }

  .footer-info {
    grid-template-columns: 1fr 1fr;
    gap: 42px 28px;
    padding: 52px 24px;
  }

  .footer-info article:last-child {
    grid-column: 1 / -1;
  }
}

@media (max-width: 480px) {
  .hero {
    min-height: 720px;
    padding-right: 20px;
    padding-left: 20px;
  }

  h1 {
    font-size: clamp(52px, 14vw, 68px);
  }

  .hero-description {
    max-width: 280px;
    font-size: 14px;
  }

  .testimonial {
    max-width: 320px;
    align-self: flex-start;
    margin-top: auto;
  }

  .footer-info {
    grid-template-columns: 1fr;
    gap: 36px;
  }

  .footer-info article:last-child {
    grid-column: auto;
  }

  .site-footer {
    flex-wrap: wrap;
    padding: 18px 20px;
  }

  .site-footer > span:nth-child(2) {
    order: 1;
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    scroll-behavior: auto !important;
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
