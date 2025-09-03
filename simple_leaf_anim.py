#!/usr/bin/env python3
from PIL import Image, ImageDraw, ImageEnhance
import os
import math

# Simple animation without numpy dependency
W, H = 1440, 1600
FPS = 30
DURATION = 3.0  # seconds
TOTAL_FRAMES = int(FPS * DURATION)

def create_simple_leaf_animation():
    print("🍃 Creating simple leaf animation...")
    
    # Try to load existing leaf image
    leaf_path = "CloudCounter/app/src/main/res/drawable/leaf_only.png"
    
    if os.path.exists(leaf_path):
        print(f"✅ Using {leaf_path}")
        leaf_img = Image.open(leaf_path).convert("RGBA")
    else:
        print("🍃 Creating simple leaf shape")
        # Create a simple leaf
        leaf_img = Image.new("RGBA", (48, 48), (0, 0, 0, 0))
        draw = ImageDraw.Draw(leaf_img)
        draw.ellipse([4, 4, 44, 44], fill=(34, 139, 34, 200))
        draw.polygon([(24, 4), (24, 20), (30, 12)], fill=(34, 139, 34, 200))
    
    # Scale leaf to target size
    leaf_img = leaf_img.resize((48, 48), Image.Resampling.LANCZOS)
    
    frames = []
    
    for frame_num in range(TOTAL_FRAMES):
        # Create transparent canvas
        canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        
        # Animation progress (0 to 1)
        t = frame_num / TOTAL_FRAMES
        
        # Create wave effect - circular expanding wave
        wave_center_x = W // 2
        wave_center_y = H // 2
        wave_radius = int(300 + 800 * t)  # Expanding radius
        
        # Place leaves in scattered pattern
        leaf_positions = [
            (200, 150), (800, 300), (400, 500), (1200, 200),
            (100, 800), (900, 600), (600, 1000), (300, 1300),
            (1100, 900), (500, 200), (1000, 1400), (150, 600),
            (750, 100), (1300, 700), (50, 1200), (850, 1100),
            (450, 750), (1150, 400), (250, 950), (950, 150)
        ]
        
        for x, y in leaf_positions:
            if x + 48 > W or y + 48 > H:
                continue
                
            # Calculate distance from wave center
            dist = math.sqrt((x - wave_center_x)**2 + (y - wave_center_y)**2)
            
            # Wave intensity based on distance from wave front
            wave_diff = abs(dist - wave_radius)
            if wave_diff < 100:  # Within wave influence
                intensity = max(0, 1 - wave_diff / 100)
                
                # Create faded leaf
                leaf_copy = leaf_img.copy()
                enhancer = ImageEnhance.Brightness(leaf_copy)
                leaf_copy = enhancer.enhance(intensity)
                
                # Paste leaf with alpha blending
                canvas.paste(leaf_copy, (int(x), int(y)), leaf_copy)
        
        frames.append(canvas)
        if frame_num % 10 == 0:
            print(f"  Generated frame {frame_num + 1}/{TOTAL_FRAMES}")
    
    # Save as GIF animation first
    gif_path = "CloudCounter/app/src/main/res/drawable/leaf_reflection_1440x1600.gif"
    frames[0].save(
        gif_path,
        save_all=True,
        append_images=frames[1:],
        duration=int(1000 / FPS),  # Duration per frame in ms
        loop=0,  # Infinite loop
        optimize=True
    )
    print(f"✅ Saved animated GIF: {gif_path}")
    
    # Try to convert to WebP
    webp_path = "CloudCounter/app/src/main/res/drawable/leaf_reflection_1440x1600.webp"
    try:
        import subprocess
        result = subprocess.run(['gif2webp', '-lossy', '-q', '85', gif_path, '-o', webp_path], 
                              capture_output=True, text=True)
        if result.returncode == 0:
            print(f"✅ Converted to WebP: {webp_path}")
            os.remove(gif_path)  # Remove GIF if WebP conversion successful
            return True
        else:
            print("⚠️ WebP conversion failed, using GIF format")
            return True
    except (subprocess.SubprocessError, FileNotFoundError):
        print("⚠️ gif2webp not available, using GIF format")
        return True

if __name__ == "__main__":
    create_simple_leaf_animation()