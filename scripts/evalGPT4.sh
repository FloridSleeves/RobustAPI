cd ..

python eval/evaluator.py \
--answer_dir=./results/gpt4/one_shot/ \
--pattern_path=./eval/pat_list.txt \
--dataset_path=./dataset/question.jsonl \
--model=gpt \
--passk=1
