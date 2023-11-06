cd ..

GPU=$1

python askHF.py \
--ckpt_path deepseek-ai/deepseek-coder-6.7b-base \
--tokenizer_path deepseek-ai/deepseek-coder-6.7b-base \
--result_dir ./results/deepseek/ \
--shot_number 1 \
--gpu $GPU
