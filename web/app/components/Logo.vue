<template>
  <div class="transit-track">
    <!-- ICON -->
    <svg
      class="transit-track__icon"
      viewBox="0 0 160 160"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <!-- Motion lines - RIGHT side -->
      <rect
        x="113"
        y="74"
        width="45"
        height="8"
        rx="4"
        fill="#FF1E2D"
      />

      <rect
        x="120"
        y="91"
        width="37"
        height="8"
        rx="4"
        fill="#FF1E2D"
      />

      <rect
        x="113"
        y="108"
        width="45"
        height="8"
        rx="4"
        fill="#FF1E2D"
      />

      <!-- Red outer transit marker -->
      <path
        d="
          M80 5
          C45 5 18 32 18 67
          C18 91 31 111 50 125
          L80 151
          L110 125
          C129 111 142 91 142 67
          C142 32 115 5 80 5
          Z
        "
        fill="#FF1E2D"
      />

      <!-- White inner circle -->
      <circle
        cx="80"
        cy="67"
        r="43"
        fill="#FFFFFF"
      />

      <!-- Train body -->
      <rect
        x="54"
        y="32"
        width="52"
        height="63"
        rx="12"
        fill="#29343A"
      />

      <!-- Roof detail -->
      <rect
        x="68"
        y="37"
        width="24"
        height="5"
        rx="2.5"
        fill="#FFFFFF"
      />

      <!-- Front window -->
      <rect
        x="63"
        y="46"
        width="34"
        height="22"
        rx="5"
        fill="#FFFFFF"
      />

      <!-- Inner window -->
      <rect
        x="67"
        y="50"
        width="26"
        height="14"
        rx="3"
        fill="#29343A"
      />

      <!-- Headlights -->
      <circle cx="66" cy="78" r="5" fill="#FFFFFF"/>
      <circle cx="94" cy="78" r="5" fill="#FFFFFF"/>

      <!-- Left rail -->
      <path
        d="M67 92 L48 137"
        stroke="#29343A"
        stroke-width="9"
        stroke-linecap="round"
      />

      <!-- Right rail -->
      <path
        d="M93 92 L112 137"
        stroke="#29343A"
        stroke-width="9"
        stroke-linecap="round"
      />

      <!-- Rail center -->
      <path
        d="M78 103 L72 129"
        stroke="#FFFFFF"
        stroke-width="4"
        stroke-linecap="round"
      />
    </svg>

    <!-- WORDMARK -->
    <div v-if="!collapsed" class="transit-track__wordmark">
      <div class="transit-track__transit">
        Transit
      </div>

      <div class="transit-track__track">
        Track
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  height: {
    type: Number,
    default: 45
  },
  collapsed: {
    type: Boolean,
    default: false
  }
})
</script>

<style scoped>
@import url(
'https://fonts.googleapis.com/css2?family=Montserrat:wght@800;900&display=swap'
);

.transit-track {
  --red: #ff1e2d;
  --dark: #29343a;

  display: inline-flex;
  align-items: center;

  height: v-bind(height+ 'px');

  gap: 1px;

  font-family: 'Montserrat', Arial, sans-serif;
}

/* Icon */
.transit-track__icon {
  width: calc(v-bind(height+ 'px') * 0.90);
  height: v-bind(height+ 'px');
  flex-shrink: 0;
}

/* Wordmark */
.transit-track__wordmark {
  display: flex;
  flex-direction: row;

  justify-content: center;

  line-height: 0.82;
  letter-spacing: -0.055em;

  font-weight: 900;
}

/* Transit */
.transit-track__transit {
  color: var(--dark);
  font-size: calc(v-bind(height+ 'px') * 0.57);
}

/* Track */
.transit-track__track {
  color: var(--red);
  font-size: calc(v-bind(height+ 'px') * 0.57);

  margin-top: 7px;
}
</style>
